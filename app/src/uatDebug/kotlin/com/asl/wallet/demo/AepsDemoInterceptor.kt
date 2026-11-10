package com.asl.wallet.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * UAT debug only, and only while [AepsDemo] is on. Answers the AEPS calls inside the app:
 * - `aeps/init` goes to the real server (real banks and providers) and the demo scanner is added to its device list;
 * - status says onboarding and bank checks are done, with the daily 2FA due until it is done once;
 * - 2FA, customer OTP and the transaction are answered here: they never reach the server, so no money moves.
 * Every answer carries `X-ASL-Demo: true`, so Chucker shows it as a demo.
 */
class AepsDemoInterceptor(private val demo: AepsDemo) : Interceptor {
    private val json = Json { ignoreUnknownKeys = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!demo.isOn()) return chain.proceed(req)
        val path = req.url.encodedPath.substringAfter("/api/v1/")
        if (!path.startsWith("merchant/aeps/") && path != "merchant/auth/me") return chain.proceed(req)
        val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        fun field(k: String) = (obj?.get(k) as? JsonPrimitive)?.contentOrNull

        return when (path) {
            "merchant/aeps/init" -> withDemoScanner(chain.proceed(req))
            "merchant/auth/me" -> withDemoIdentity(chain.proceed(req))
            "merchant/aeps/checkAgentStatus" -> reply(req, ok("Agent status (demo)", buildJsonObject {
                put("isEkycDone", true); put("isBankEkycDone", true); put("isBioKyc", true); put("isTwoFA", demo.twoFactorDone)
                put("providerId", field("providerId")?.toIntOrNull() ?: 2); put("outletid", "DEMO-OUTLET"); put("pidOptionWadh", ""); put("referenceKey", "DEMO")
                put("bcAgentData", JsonObject(emptyMap()))
            }))
            "merchant/aeps/two-factor-authenticate" -> { demo.twoFactorDone = true; reply(req, ok("Daily login successful (demo)", JsonObject(emptyMap()))) }
            "merchant/aeps/generate-otp" -> reply(req, ok("OTP sent to the customer (demo: any 6 digits work)", buildJsonObject { put("fpTransactionId", "DEMOFP" + digits(10)) }))
            "merchant/aeps/transaction" -> reply(req, ok("Transaction successful (DEMO: no money moved)", transaction(field("txnType"), field("amount"))))
            // Anything else (onboarding, KYC): a plain demo success, so nothing real is touched.
            else -> reply(req, ok("Done (demo)", JsonObject(emptyMap())))
        }
    }

    private fun transaction(type: String?, amount: String?): JsonObject = buildJsonObject {
        val now = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH).format(Date())
        put("aepsmode", type ?: "BE")
        put("timestamp", now)
        put("txnReferenceId", "DEMO" + digits(8))
        put("bank", "DEMO BANK (no money moved)")
        put("bankAccountBalance", "15,234.50")
        put("transactionAmount", if (type == "CW" || type == "CD") amount ?: "0" else "0")
        put("miniStatementData", if (type == "MS") buildJsonArray {
            listOf(
                Triple("02/11", "Cr", "UPI/DEMO SALARY" to "12,000.00"), Triple("04/11", "Dr", "ATM WDL DEMO" to "2,000.00"),
                Triple("06/11", "Dr", "POS DEMO STORE" to "640.50"), Triple("08/11", "Cr", "NEFT DEMO REFUND" to "1,250.00"),
                Triple("09/11", "Dr", "AEPS CW DEMO" to "1,500.00"),
            ).forEach { (d, t, n) -> add(buildJsonObject { put("date", d); put("txnType", t); put("narration", n.first); put("amount", n.second) }) }
        } else JsonArray(emptyList()))
    }

    /** Real init, plus the demo scanner at the top of the device list. */
    private fun withDemoScanner(real: Response): Response {
        val text = real.peekBody(1_000_000).string()
        val top = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val data = (top?.get("data") as? JsonObject)
        val demoDevice = buildJsonObject { put("name", "ASL Demo Scanner (UAT)"); put("deviceId", "demo"); put("isActive", 1) }
        val newData = if (data != null) JsonObject(data + ("deviceInfo" to JsonArray(listOf(demoDevice) + ((data["deviceInfo"] as? JsonArray) ?: JsonArray(emptyList())))))
        else buildJsonObject {
            put("deviceInfo", JsonArray(listOf(demoDevice)))
            put("providerInfo", buildJsonArray { add(buildJsonObject { put("name", "Kotak"); put("providerId", 2); put("isActive", 1) }) })
            put("bankList", buildJsonArray {
                listOf("State Bank of India" to "607094", "Bank of Baroda" to "606985", "Punjab National Bank" to "607027")
                    .forEach { (n, iin) -> add(buildJsonObject { put("bankName", n); put("bankIino", iin) }) }
            })
            put("allowedAmount", buildJsonArray { listOf(500, 1000, 2000, 5000).forEach { add(JsonPrimitive(it)) } })
        }
        real.close()
        return rebuild(real, JsonObject((top ?: JsonObject(emptyMap())) + mapOf("status" to JsonPrimitive(true), "data" to newData)).toString())
    }

    /** AEPS needs the agent's Aadhaar and mobile from the profile; a demo pair stands in when they are missing. */
    private fun withDemoIdentity(real: Response): Response {
        val text = real.peekBody(1_000_000).string()
        val top = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return real
        val data = top["data"] as? JsonObject ?: return real
        val aadhaar = (data["aadharno"] as? JsonPrimitive)?.contentOrNull?.filter(Char::isDigit).orEmpty()
        val phone = (data["phone"] as? JsonPrimitive)?.contentOrNull?.filter(Char::isDigit).orEmpty()
        if (aadhaar.length == 12 && phone.length == 10) return real
        real.close()
        val fixed = data + mapOf("aadharno" to JsonPrimitive("999900001111"), "phone" to JsonPrimitive(if (phone.length == 10) phone else "9999900000"))
        return rebuild(real, JsonObject(top + ("data" to JsonObject(fixed))).toString())
    }

    private fun ok(message: String, data: JsonObject) = buildJsonObject { put("status", true); put("statusCode", 200); put("message", message); put("data", data) }.toString()

    private fun reply(req: okhttp3.Request, body: String) = Response.Builder()
        .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .header("Content-Type", "application/json; charset=utf-8").header("X-ASL-Demo", "true")
        .body(body.toResponseBody(JSON)).build()

    private fun rebuild(real: Response, body: String) = real.newBuilder().removeHeader("Content-Length").header("X-ASL-Demo", "true")
        .body(body.toResponseBody(JSON)).build()

    private fun digits(n: Int) = (1..n).joinToString("") { Random.nextInt(10).toString() }

    private companion object { val JSON = "application/json; charset=utf-8".toMediaType() }
}
