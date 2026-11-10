package com.asl.wallet.demo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * UAT debug only. Plays the ASL backend for the four Add Money APIs, following the PayU spec shared with the
 * backend team, so the app's real flow can be shown end to end before the backend builds it.
 *
 * With a PayU **test** key and salt (from local.properties, never committed) the real PayU test screens open and
 * `status` asks PayU's test `verify_payment`. Without them the key is [NO_PAYU_KEY] and a simulated PayU screen
 * stands in. Either way nothing touches ASL's real PayU account, and no real wallet is credited.
 */
class AddMoneyDemoServer(
    private val credentials: Credentials?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    /** Server → PayU `verify_payment` (form POST). Returns PayU's raw JSON, or null if unreachable. */
    private val verifyPayment: ((form: Map<String, String>) -> String?)? = null,
    /** Notes for the demo log (what a real server would do at each step). */
    private val note: (title: String, text: String) -> Unit = { _, _ -> },
) {
    data class Credentials(val key: String, val salt: String)
    data class Reply(val code: Int, val body: String)

    private class Order(
        val txnid: String, val amount: BigDecimal, val createdAt: Long, val idemKey: String?,
        var status: String = "INITIATED", var mihpayid: String? = null, var bankRef: String? = null,
        var mode: String? = null, var reason: String? = null, var credited: Boolean = false, var completedAt: Long? = null,
    )

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }
    private val orders = LinkedHashMap<String, Order>()
    private val byIdemKey = HashMap<String, String>()
    private var lastBalance: BigDecimal = BigDecimal("1022.00")

    val key: String get() = credentials?.key ?: NO_PAYU_KEY
    private val salt: String get() = credentials?.salt ?: NO_PAYU_SALT
    val usesRealPayU: Boolean get() = credentials != null

    /** Whether [txnid] is an order this demo created (so its later calls may be answered here). */
    @Synchronized
    fun knows(txnid: String?): Boolean = txnid != null && orders.containsKey(txnid)

    // ---------------------------------------------------------------- 1 · GET merchant/my-wallet/add-money

    /** [realBalance] comes from the real UAT API when it answered, so the screen shows the retailer's balance. */
    @Synchronized
    fun info(realBalance: BigDecimal?): Reply {
        realBalance?.let { lastBalance = it }
        return ok(200, "Add money data fetched successfully", buildJsonObject {
            putJsonObject("wallet") { put("balanceAmt", money(lastBalance)) }
            putJsonObject("limits") { put("minAmount", MIN.toInt()); put("maxAmount", MAX.toInt()) }
            put("paymentModes", Json.parseToJsonElement("[\"PAYU\"]"))
            putJsonObject("charges") { put("type", "PERCENT"); put("value", "0.00"); put("note", "No charges") }
        })
    }

    // ---------------------------------------------------------------- 2 · POST payments/payu/initiate

    @Synchronized
    fun createOrder(idempotencyKey: String?, body: String): Reply {
        val amount = runCatching { Json.parseToJsonElement(body).jsonObject["amount"]?.jsonPrimitive?.contentOrNull?.toBigDecimal() }.getOrNull()
            ?: return fail(400, "Enter the amount")
        if (amount < MIN || amount > MAX) return fail(400, "Enter an amount between ₹1,000 and ₹10,000")
        val amt = amount.setScale(2, RoundingMode.HALF_UP)

        if (idempotencyKey != null) byIdemKey[idempotencyKey]?.let { txn ->
            val o = orders.getValue(txn)
            if (o.amount.compareTo(amt) != 0) return fail(409, "This request was already used for a different amount")
            note("Server: same Idempotency-Key", "Returned the order already made for this key ($txn). No second order was created.")
            return ok(201, "Order created", orderJson(o))
        }
        val now = clock()
        val txnid = "ASLD" + stamp(now, "yyMMddHHmmss") + random.nextInt(1000, 9999)
        val o = Order(txnid, amt, now, idempotencyKey)
        orders[txnid] = o
        idempotencyKey?.let { byIdemKey[it] = txnid }
        note(
            "Server: order created",
            "Saved $txnid as INITIATED for ₹${money(amt)}. Payment hash = sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT).",
        )
        return ok(201, "Order created", orderJson(o))
    }

    private fun orderJson(o: Order): JsonObject {
        val amount = money(o.amount)
        val paymentString = listOf(key, o.txnid, amount, PRODUCT, FIRST_NAME, EMAIL, UDF1, "", "", "", "", "", "", "", "", "", "").joinToString("|")
        return buildJsonObject {
            put("txnid", o.txnid)
            put("clientTxnId", o.txnid)
            put("amount", amount)
            put("status", if (o.status == "INITIATED") "PENDING" else o.status)
            put("expiresAt", stamp(o.createdAt + ORDER_TTL_MS, "yyyy-MM-dd'T'HH:mm:ssXXX"))
            putJsonObject("payu") {
                put("key", key)
                put("txnid", o.txnid)
                put("amount", amount)
                put("productinfo", PRODUCT)
                put("firstname", FIRST_NAME)
                put("email", EMAIL)
                put("phone", PHONE)
                put("surl", RETURN_URL)
                put("furl", FAIL_URL)
                put("udf1", UDF1); put("udf2", ""); put("udf3", ""); put("udf4", ""); put("udf5", "")
                put("hash", sha512(paymentString + salt))
                put("payment_related_details_for_mobile_sdk", sha512("$key|payment_related_details_for_mobile_sdk|default|$salt"))
                put("vas_for_mobile_sdk", sha512("$key|vas_for_mobile_sdk|default|$salt"))
                put("environment", "TEST")
            }
        }
    }

    // ---------------------------------------------------------------- 3 · POST payments/payu/hash

    @Synchronized
    fun hash(body: String): Reply {
        val o = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return fail(400, "Bad request")
        val txnid = o.str("txnid"); val name = o.str("hashName"); val data = o.str("hashString")
        val postSalt = o.str("postSalt"); val type = o.str("hashType")
        val order = txnid?.let { orders[it] }
        val refuse = when {
            name.isNullOrBlank() || data.isNullOrBlank() -> "hashName and hashString are required"
            order == null -> "txnid is not one of this retailer's orders"
            order.status != "INITIATED" -> "order is already ${order.status}"
            clock() - order.createdAt > ORDER_TTL_MS -> "order is older than 30 minutes"
            !data.startsWith("$key|") -> "hashString doesn't start with ASL's own key"
            name == LOOKUP_HASH -> "lookup_api_hash needs the merchant secret and isn't used"
            name in PAYMENT_HASHES && !(data.contains("|${order.txnid}|") && data.contains("|${money(order.amount)}|")) ->
                "payment hash string doesn't match this order's txnid and amount"
            else -> null
        }
        if (refuse != null) {
            note("Server: hash refused", "$name → 403 ($refuse)")
            return fail(403, "Hash request not allowed")
        }
        if (name !in KNOWN_HASHES) note("Server: hash name not in the allow-list yet", "\"$name\" was signed for the demo. Add it to the backend's allow-list.")
        val h = if (type == V2) hmacSha256(data!!, salt) else sha512(data!! + salt + (postSalt ?: ""))
        note("Server: hash made", "$name (${if (type == V2) "V2 · HMAC-SHA256" else "SHA-512"}${if (postSalt != null) " · with postSalt" else ""})")
        return ok(200, "Hash generated", buildJsonObject { put("hash", h) })
    }

    // ---------------------------------------------------------------- PayU → server (simulated surl/furl)

    /** What PayU posts to surl/furl, built the way PayU does, so the backend can see the fields. Used by the simulator. */
    @Synchronized
    fun simulatedCallback(txnid: String, success: Boolean): Map<String, String>? {
        val o = orders[txnid] ?: return null
        val status = if (success) "success" else "failure"
        val amount = money(o.amount)
        val mihpayid = "40399" + (1..13).joinToString("") { random.nextInt(10).toString() }
        val reverse = listOf(salt, status, "", "", "", "", "", "", "", "", "", UDF1, EMAIL, FIRST_NAME, PRODUCT, amount, txnid, key).joinToString("|")
        val form = linkedMapOf(
            "mihpayid" to mihpayid, "mode" to "UPI", "status" to status, "unmappedstatus" to if (success) "captured" else "failed",
            "key" to key, "txnid" to txnid, "amount" to amount, "productinfo" to PRODUCT, "firstname" to FIRST_NAME,
            "email" to EMAIL, "phone" to PHONE, "udf1" to UDF1, "udf2" to "", "udf3" to "", "udf4" to "", "udf5" to "",
            "bank_ref_num" to if (success) "4312" + (1..8).joinToString("") { random.nextInt(10).toString() } else "",
            "error" to if (success) "E000" else "E308", "error_Message" to if (success) "No Error" else "Transaction failed by bank",
            "hash" to sha512(reverse),
        )
        return form
    }

    /**
     * Server side of `POST payments/payu/success|failure` (and the webhook): check the reverse hash, find the order, then settle it once.
     * With a real PayU test account the server would also call verify_payment here; the simulated payment can't be verified.
     */
    @Synchronized
    fun receiveCallback(form: Map<String, String>): Boolean {
        val o = form["txnid"]?.let { orders[it] } ?: return false.also { note("Server: callback ignored", "Unknown txnid") }
        val f = { k: String -> form[k].orEmpty() }
        val expected = sha512(listOf(salt, f("status"), "", "", "", "", "", f("udf5"), f("udf4"), f("udf3"), f("udf2"), f("udf1"), f("email"), f("firstname"), f("productinfo"), f("amount"), f("txnid"), key).joinToString("|"))
        if (expected != f("hash")) { note("Server: callback rejected", "Reverse hash doesn't match. Nothing credited."); return false }
        if (f("amount").toBigDecimalOrNull()?.compareTo(o.amount) != 0) { note("Server: callback rejected", "Amount differs from the order. Nothing credited."); return false }
        note("Server: callback checked", "Reverse hash OK, amount matches the order (₹${money(o.amount)}).")
        val success = f("status") == "success"
        settle(o, success, f("mihpayid"), f("bank_ref_num").ifBlank { null }, f("mode").ifBlank { null }, if (success) null else f("error_Message").ifBlank { "Payment failed" })
        return true
    }

    private fun settle(o: Order, success: Boolean, mihpayid: String?, bankRef: String?, mode: String?, reason: String?) {
        if (o.status != "INITIATED") return
        o.status = if (success) "SUCCESS" else "FAILED"
        o.mihpayid = mihpayid; o.bankRef = bankRef; o.mode = mode; o.reason = reason; o.completedAt = clock()
        if (success) {
            o.credited = true
            lastBalance += o.amount
            note("Server: wallet credited (demo)", "Credited ₹${money(o.amount)} once for ${o.txnid} and added a ledger entry. Demo only: your real UAT balance doesn't change.")
        }
    }

    // ---------------------------------------------------------------- POST payments/payu/verify

    /**
     * The app calls this once PayU says "success". The server confirms with PayU itself (verify_payment) and credits
     * once; the mihpayid in the body is only a hint. Answers with the same shape as status.
     */
    @Synchronized
    fun verifyRequest(body: String): Reply {
        val txn = runCatching { Json.parseToJsonElement(body).jsonObject.str("txnid") }.getOrNull() ?: return fail(400, "txnid is required")
        if (orders[txn] == null) return fail(404, "Transaction not found")
        note("Server: verify asked", "Confirms $txn with PayU (verify_payment) before any credit; the app's mihpayid is not trusted.")
        return status(txn)
    }

    // ---------------------------------------------------------------- GET payments/payu/{txnid}/status

    @Synchronized
    fun status(txnid: String): Reply {
        val o = orders[txnid] ?: return fail(404, "Transaction not found")
        if (o.status == "INITIATED" && usesRealPayU && verifyPayment != null) verify(o)
        if (o.status == "INITIATED" && clock() - o.createdAt > ORDER_TTL_MS) {
            o.status = "FAILED"; o.reason = "Payment not completed"; o.completedAt = clock()
        }
        val ok = o.status == "SUCCESS"
        return ok(200, "Payment status fetched successfully", buildJsonObject {
            put("txnid", o.txnid)
            put("amount", money(o.amount))
            put("status", if (o.status == "INITIATED") "PENDING" else o.status)
            put("isPaymentComplete", ok)
            put("walletCredited", o.credited)
            put("paymentMode", o.mode)
            put("mihpayid", o.mihpayid)
            put("bankRefNo", o.bankRef)
            put("reason", o.reason)
            put("balanceAfter", if (ok) money(lastBalance) else null)
            put("completedAt", o.completedAt?.let { stamp(it, "yyyy-MM-dd'T'HH:mm:ssXXX") })
        })
    }

    /** Server → PayU test `verify_payment`, then settle the order once. */
    private fun verify(o: Order) {
        val form = linkedMapOf("key" to key, "command" to "verify_payment", "var1" to o.txnid, "hash" to sha512("$key|verify_payment|${o.txnid}|$salt"))
        val raw = verifyPayment?.invoke(form) ?: return
        val d = runCatching { Json.parseToJsonElement(raw).jsonObject["transaction_details"]?.jsonObject?.get(o.txnid)?.jsonObject }.getOrNull() ?: return
        val st = d.str("status")?.lowercase()
        val paid = d.str("amt")?.toBigDecimalOrNull()
        when {
            st == "success" && paid != null && paid.compareTo(o.amount) == 0 -> settle(o, true, d.str("mihpayid"), d.str("bank_ref_num"), d.str("mode"), null)
            st == "success" -> note("Server: amount mismatch", "PayU says ₹$paid but the order is ₹${money(o.amount)}. Not credited.")
            st == "failure" || st == "failed" || st == "dropped" || st == "bounced" ->
                settle(o, false, d.str("mihpayid"), null, d.str("mode"), d.str("error_Message") ?: d.str("field9") ?: "Payment failed")
            else -> note("Server: still pending", "PayU says \"${st ?: "not found"}\" for ${o.txnid}.")
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun ok(code: Int, message: String, data: JsonElement) =
        Reply(code, json.encodeToString(JsonObject.serializer(), buildJsonObject { put("status", true); put("message", message); put("data", data) }))

    private fun fail(code: Int, message: String) =
        Reply(code, json.encodeToString(JsonObject.serializer(), buildJsonObject { put("status", false); put("message", message) }))

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun stamp(ms: Long, pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = IST }.format(Date(ms))

    companion object {
        val MIN = BigDecimal(1000)
        val MAX = BigDecimal(10000)
        const val ORDER_TTL_MS = 30 * 60 * 1000L
        const val NO_PAYU_KEY = "DEMO_NO_PAYU_KEY"
        private const val NO_PAYU_SALT = "DEMO_NO_PAYU_SALT"
        const val PRODUCT = "ASL Wallet top-up"
        const val FIRST_NAME = "Demo Retailer"
        const val EMAIL = "demo.retailer@example.com"
        const val PHONE = "9999999999"
        const val UDF1 = "100001"
        const val RETURN_URL = "https://www.testproject.co.in/app/api/v1/payments/payu/success"
        const val FAIL_URL = "https://www.testproject.co.in/app/api/v1/payments/payu/failure"
        const val V2 = "V2"
        private const val LOOKUP_HASH = "lookup_api_hash"
        private val PAYMENT_HASHES = setOf("payment_hash", "payment_source", "si_payment_source", "tpv_payment_source")
        val KNOWN_HASHES = setOf(
            "payment_related_details_for_mobile_sdk", "vas_for_mobile_sdk", "get_eligible_payment_options", "get_checkout_details",
            "payment_source", "payment_hash", "check_offer_status", "get_offer_details", "validate_offer_details", "check_balance",
        )
        private val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

        fun money(v: BigDecimal): String = v.setScale(2, RoundingMode.HALF_UP).toPlainString()

        fun sha512(s: String): String =
            MessageDigest.getInstance("SHA-512").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

        @OptIn(ExperimentalEncodingApi::class)
        fun hmacSha256(data: String, key: String): String {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
            return Base64.Default.encode(mac.doFinal(data.toByteArray()))
        }
    }
}
