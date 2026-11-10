package com.asl.wallet.demo

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import kotlin.random.Random

class AddMoneyDemoServerTest {

    private val creds = AddMoneyDemoServer.Credentials("TESTKEY", "TESTSALT")
    private var now = 1_794_000_000_000L
    private val notes = mutableListOf<String>()
    private fun server(c: AddMoneyDemoServer.Credentials? = creds, verify: ((Map<String, String>) -> String?)? = null) =
        AddMoneyDemoServer(c, clock = { now }, random = Random(7), verifyPayment = verify, note = { t, _ -> notes += t })

    private fun data(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun `order carries every PayU field the app needs, with the standard payment hash`() {
        val r = server().createOrder("k1", """{"amount":"1500"}""")
        assertThat(r.code).isEqualTo(201)
        val payu = data(r.body)["payu"]!!.jsonObject
        listOf("key", "txnid", "amount", "productinfo", "firstname", "email", "surl", "furl", "hash").forEach { assertThat(payu.s(it)).isNotEmpty() }
        assertThat(payu.s("amount")).isEqualTo("1500.00")
        assertThat(payu.s("txnid").length).isAtMost(25)
        val expected = AddMoneyDemoServer.sha512(
            "TESTKEY|${payu.s("txnid")}|1500.00|ASL Wallet top-up|Demo Retailer|demo.retailer@example.com|100001||||||||||TESTSALT",
        )
        assertThat(payu.s("hash")).isEqualTo(expected)
        assertThat(payu.s("payment_related_details_for_mobile_sdk"))
            .isEqualTo(AddMoneyDemoServer.sha512("TESTKEY|payment_related_details_for_mobile_sdk|default|TESTSALT"))
    }

    @Test fun `amount limits, and the Idempotency-Key returns the same order or 409`() {
        val s = server()
        assertThat(s.createOrder("a", """{"amount":"999"}""").code).isEqualTo(400)
        assertThat(s.createOrder("a", """{"amount":"10001"}""").code).isEqualTo(400)
        val first = data(s.createOrder("k", """{"amount":"2000"}""").body).s("txnid")
        val again = s.createOrder("k", """{"amount":"2000.00"}""")
        assertThat(again.code).isEqualTo(201)
        assertThat(data(again.body).s("txnid")).isEqualTo(first)
        assertThat(s.createOrder("k", """{"amount":"3000"}""").code).isEqualTo(409)
    }

    @Test fun `hash API signs only this retailer's own open order, with postSalt and V2`() {
        val s = server()
        val txn = data(s.createOrder("k", """{"amount":"1500"}""").body).s("txnid")
        fun ask(name: String, str: String, post: String? = null, type: String? = null) =
            s.hash("""{"txnid":"$txn","hashName":"$name","hashString":${kotlinx.serialization.json.JsonPrimitive(str).toString()},"postSalt":${post?.let { "\"$it\"" }},"hashType":${type?.let { "\"$it\"" }}}""")
        val okReply = ask("get_eligible_payment_options", "TESTKEY|get_eligible_payment_options|{}|")
        assertThat(data(okReply.body).s("hash")).isEqualTo(AddMoneyDemoServer.sha512("TESTKEY|get_eligible_payment_options|{}|TESTSALT"))
        assertThat(data(ask("x", "TESTKEY|x|", post = "PS").body).s("hash")).isEqualTo(AddMoneyDemoServer.sha512("TESTKEY|x|TESTSALTPS"))
        assertThat(data(ask("payment_source", "TESTKEY|$txn|1500.00|", type = "V2").body).s("hash"))
            .isEqualTo(AddMoneyDemoServer.hmacSha256("TESTKEY|$txn|1500.00|", "TESTSALT"))

        assertThat(ask("get_checkout_details", "OTHERKEY|get_checkout_details|").code).isEqualTo(403)
        assertThat(ask("payment_source", "TESTKEY|$txn|9999.00|").code).isEqualTo(403)
        assertThat(ask("lookup_api_hash", "TESTKEY|lookup|").code).isEqualTo(403)
        assertThat(s.hash("""{"txnid":"NOPE","hashName":"a","hashString":"TESTKEY|a|"}""").code).isEqualTo(403)
        now += AddMoneyDemoServer.ORDER_TTL_MS + 1
        assertThat(ask("get_checkout_details", "TESTKEY|get_checkout_details|").code).isEqualTo(403)
    }

    @Test fun `callback has a valid reverse hash, credits once, and status reports it`() {
        val s = server(c = null)
        val txn = data(s.createOrder("k", """{"amount":"1500"}""").body).s("txnid")
        assertThat(data(s.status(txn).body).s("status")).isEqualTo("PENDING")
        val form = s.simulatedCallback(txn, success = true)!!
        val reverse = AddMoneyDemoServer.sha512(
            listOf("DEMO_NO_PAYU_SALT", "success", "", "", "", "", "", "", "", "", "", "100001", form["email"], form["firstname"], form["productinfo"], "1500.00", txn, "DEMO_NO_PAYU_KEY").joinToString("|"),
        )
        assertThat(form["hash"]).isEqualTo(reverse)
        assertThat(s.receiveCallback(form + ("amount" to "1.00"))).isFalse()
        assertThat(s.receiveCallback(form + ("hash" to "forged"))).isFalse()
        assertThat(data(s.status(txn).body).s("status")).isEqualTo("PENDING")
        assertThat(s.receiveCallback(form)).isTrue()
        assertThat(s.receiveCallback(form)).isTrue() // a second callback (e.g. the webhook) must not credit again
        val st = data(s.status(txn).body)
        assertThat(st.s("status")).isEqualTo("SUCCESS")
        assertThat(st["isPaymentComplete"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(st.s("balanceAfter")).isEqualTo("2522.00")
        assertThat(notes.count { it.startsWith("Server: wallet credited") }).isEqualTo(1)
        assertThat(s.status("SOMEONE_ELSE").code).isEqualTo(404)
    }

    @Test fun `status asks PayU verify_payment and credits only when the amount matches`() {
        var form: Map<String, String>? = null
        val s = server(verify = { f ->
            form = f
            """{"status":1,"transaction_details":{"${f["var1"]}":{"status":"success","amt":"1500.00","mihpayid":"403993","bank_ref_num":"4312","mode":"UPI"}}}"""
        })
        val txn = data(s.createOrder("k", """{"amount":"1500"}""").body).s("txnid")
        assertThat(data(s.status(txn).body).s("status")).isEqualTo("SUCCESS")
        assertThat(form!!["hash"]).isEqualTo(AddMoneyDemoServer.sha512("TESTKEY|verify_payment|$txn|TESTSALT"))

        val wrong = server(verify = { f -> """{"transaction_details":{"${f["var1"]}":{"status":"success","amt":"1.00"}}}""" })
        val t2 = data(wrong.createOrder("k", """{"amount":"1500"}""").body).s("txnid")
        assertThat(data(wrong.status(t2).body).s("status")).isEqualTo("PENDING")
    }

    @Test fun `old open orders expire as FAILED`() {
        val s = server(c = null)
        val txn = data(s.createOrder("k", """{"amount":"1500"}""").body).s("txnid")
        now += AddMoneyDemoServer.ORDER_TTL_MS + 1
        val st = data(s.status(txn).body)
        assertThat(st.s("status")).isEqualTo("FAILED")
        assertThat(st.s("reason")).isEqualTo("Payment not completed")
    }

    /** Writes a sample request/response log (build/add-money-demo-sample.md) for the backend spec. */
    @Test fun `sample log`() {
        val out = StringBuilder()
        val s = AddMoneyDemoServer(null, clock = { now }, random = Random(11), note = { t, x -> out.append("> **$t** · $x\n\n") })
        fun call(method: String, path: String, body: String?, reply: AddMoneyDemoServer.Reply, extra: String = "") {
            out.append("```http\n$method /api/v1/$path\nAuthorization: Bearer <token>\n$extra")
            if (body != null) out.append("Content-Type: application/json\n\n$body\n")
            out.append("```\n```http\nHTTP ${reply.code}\nContent-Type: application/json\n\n${reply.body}\n```\n\n")
        }
        call("GET", "merchant/my-wallet/add-money", null, s.info(BigDecimal("1022.00")))
        val create = s.createOrder("3f1c9a52-6a0e-4b7e-9f1d-2c2b8e7a41d0", """{"amount":"1500.00"}""")
        call("POST", "payments/payu/initiate", """{"amount":1500,"paymentPurpose":"ASL Wallet Topup","isAndroid":true}""", create, "Idempotency-Key: 3f1c9a52-6a0e-4b7e-9f1d-2c2b8e7a41d0\n")
        val txn = data(create.body).s("txnid")
        val hashBody = """{"txnid":"$txn","hashName":"get_eligible_payment_options","hashString":"DEMO_NO_PAYU_KEY|get_eligible_payment_options|{\"amount\":\"1500.00\",\"txnid\":\"$txn\"}|","postSalt":null,"hashType":null}"""
        call("POST", "payments/payu/hash", hashBody, s.hash(hashBody))
        val form = s.simulatedCallback(txn, true)!!
        out.append("```http\nPOST /api/v1/payments/payu/success   (PayU → server)\nContent-Type: application/x-www-form-urlencoded\n\n${form.entries.joinToString("&") { "${it.key}=${it.value}" }}\n```\n\n")
        s.receiveCallback(form)
        call("POST", "payments/payu/verify", """{"txnid":"$txn","mihpayid":"${form["mihpayid"]}"}""", s.verifyRequest("""{"txnid":"$txn","mihpayid":"${form["mihpayid"]}"}"""))
        call("GET", "payments/payu/$txn/status", null, s.status(txn))
        File("build").mkdirs()
        File("build/add-money-demo-sample.md").writeText(out.toString())
        assertThat(out.toString()).contains("\"status\": \"SUCCESS\"")
    }
}
