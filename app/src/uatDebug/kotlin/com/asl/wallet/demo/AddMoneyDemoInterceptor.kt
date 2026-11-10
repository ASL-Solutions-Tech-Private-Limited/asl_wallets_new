package com.asl.wallet.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

/**
 * UAT debug only. Add Money's API calls (the balance call and the backend's PayU routes: payments/payu/initiate,
 * hash, verify, status) go to the **real server first**. Only when the server doesn't have the route yet (404) does
 * [server] answer instead, so the flow can still be shown. Once the routes are on UAT, the real PayU SDK opens with
 * the backend's live key and hashes, with no new build. Every request and response, real or demo, goes to [log].
 */
class AddMoneyDemoInterceptor(private val server: AddMoneyDemoServer, private val log: DemoLog) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val path = req.url.encodedPath.substringAfter("/api/v1/")
        val isInfo = path == BASE && req.method == "GET"
        val isPayU = path.startsWith("$PAYU/")
        if (!isInfo && !isPayU) return chain.proceed(req)

        val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
        // Opening Add Money starts a fresh log, so one shared log is one payment attempt.
        if (isInfo) log.clear()

        val real = runCatching { chain.proceed(req) }.getOrElse { if (isInfo) throw it else null }
        // Only initiate may fall back to the demo when the route is missing. Hash, verify and status fall back only for
        // orders the demo itself made: a real order always gets the real server's answer, 404 included.
        val demoOrder = when {
            path == "$PAYU/initiate" -> true
            path.endsWith("/status") -> server.knows(path.removePrefix("$PAYU/").removeSuffix("/status"))
            else -> server.knows(txnidOf(body))
        }
        if (real != null && (real.code != 404 || isInfo || !demoOrder)) {
            // The server has this route: use its answer as it is, and keep a copy in the log.
            val text = real.peekBody(MAX_LOG).string()
            if (isInfo) runCatching { server.info(balanceOf(text)) } // remembers the balance for the demo
            log.http(req.method, req.url.toString(), headersOf(req), body, real.code, listOf("X-ASL-Source" to "UAT server"), text)
            return real
        }
        real?.close()

        val reply = when {
            path == "$PAYU/initiate" && req.method == "POST" -> server.createOrder(req.header("Idempotency-Key"), body.orEmpty())
            path == "$PAYU/hash" && req.method == "POST" -> server.hash(body.orEmpty())
            path == "$PAYU/verify" && req.method == "POST" -> server.verifyRequest(body.orEmpty())
            path.endsWith("/status") && req.method == "GET" -> server.status(path.removePrefix("$PAYU/").removeSuffix("/status"))
            else -> AddMoneyDemoServer.Reply(404, """{"status":false,"message":"Not found"}""")
        }
        val headers = listOf("Content-Type" to "application/json; charset=utf-8", "X-ASL-Demo" to "true")
        log.http(req.method, req.url.toString() + "  (demo: route not on the server yet)", headersOf(req), body, reply.code, headers, reply.body)
        return Response.Builder()
            .request(req).protocol(Protocol.HTTP_1_1).code(reply.code).message(MESSAGES[reply.code] ?: "OK")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(reply.body.toResponseBody(JSON))
            .build()
    }

    private fun headersOf(req: okhttp3.Request) =
        req.headers.map { it.first to it.second }.filterNot { it.first.equals("User-Agent", true) } +
            listOfNotNull(req.body?.contentType()?.let { "Content-Type" to it.toString() })

    private fun txnidOf(body: String?): String? = runCatching {
        Json.parseToJsonElement(body.orEmpty()).jsonObject["txnid"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun balanceOf(text: String) = runCatching {
        val o = Json.parseToJsonElement(text).jsonObject["data"]?.jsonObject?.get("wallet")?.jsonObject
        (o?.get("balanceAmt") ?: o?.get("balance_amt"))?.jsonPrimitive?.contentOrNull?.toBigDecimalOrNull()
    }.getOrNull()

    companion object {
        const val BASE = "merchant/my-wallet/add-money"
        /** The backend's PayU Checkout Pro routes (collection "ASL PayU Checkout Pro API"). */
        const val PAYU = "payments/payu"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_LOG = 250_000L
        private val MESSAGES = mapOf(200 to "OK", 201 to "Created", 400 to "Bad Request", 403 to "Forbidden", 404 to "Not Found", 409 to "Conflict")
    }
}
