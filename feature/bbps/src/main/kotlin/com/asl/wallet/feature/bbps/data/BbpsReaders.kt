package com.asl.wallet.feature.bbps.data

import com.asl.wallet.feature.bbps.domain.BillCategory
import com.asl.wallet.feature.bbps.domain.Biller
import com.asl.wallet.feature.bbps.domain.BillerInfo
import com.asl.wallet.feature.bbps.domain.BillerParam
import com.asl.wallet.feature.bbps.domain.FetchedBill
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * BBPS answers are read field by field (internal for tests). The BBPS switch sends single items as an object
 * and several as an array (the old app needed custom adapters for that): [items] accepts both.
 */

internal fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
    (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "-" && !it.equals("null", true) && !it.equals("NA", true) }
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

/** An array, or a single object as a one-item list. */
internal fun JsonElement?.items(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(this)
    else -> emptyList()
}

/** The first object (depth-first, this one included) that has [key]: the answer's nesting differs between servers. */
internal fun JsonElement?.withKey(key: String, depth: Int = 5): JsonObject? {
    if (depth < 0) return null
    return when (this) {
        is JsonObject -> if (containsKey(key)) this else values.firstNotNullOfOrNull { it.withKey(key, depth - 1) }
        is JsonArray -> firstNotNullOfOrNull { it.withKey(key, depth - 1) }
        else -> null
    }
}

/** The first array found at [keys] in this object or its `data` / `data.data`, or this element if it is an array. */
private fun JsonElement?.listAt(vararg keys: String): List<JsonObject> {
    if (this is JsonArray) return items()
    val o = this.obj() ?: return emptyList()
    for (scope in listOf(o, o["data"].obj(), o["data"].obj()?.get("data").obj())) {
        if (scope == null) continue
        for (k in keys) (scope[k] as? JsonArray)?.let { return it.items() }
    }
    return (o["data"] as? JsonArray)?.items() ?: (o["data"].obj()?.get("data") as? JsonArray)?.items() ?: emptyList()
}

private fun active(o: JsonObject): Boolean {
    val p = o["status"] as? JsonPrimitive ?: return true
    p.booleanOrNull?.let { return it }
    return p.contentOrNull?.trim()?.lowercase() !in setOf("0", "false", "inactive", "no")
}

internal fun readCategories(e: JsonElement): List<BillCategory> = e.listAt("categories", "category", "data")
    .filter(::active)
    .mapNotNull { o -> o.str("name", "category_name", "categoryName", "category")?.let(::BillCategory) }
    .distinctBy { it.name.lowercase() }

internal fun readBillers(e: JsonElement, category: String): List<Biller> = e.listAt("billers", "biller", "data")
    .filter(::active)
    .mapNotNull { o ->
        val code = o.str("operator_code", "operatorCode", "billerId", "biller_id", "code") ?: return@mapNotNull null
        val name = o.str("name", "billerName", "biller_name", "operator_name") ?: return@mapNotNull null
        Biller(code, name, o.str("category", "billerCategory") ?: category)
    }
    .distinctBy { it.code }
    .sortedBy { it.name.lowercase() }

/** The biller's MDM record: `…mdmRequestNew.biller.billerInputParams.paramInfo` (one object or a list). */
internal fun readBillerInfo(e: JsonElement, biller: Biller): BillerInfo? {
    val inputs = e.withKey("billerInputParams")?.get("billerInputParams") ?: e.withKey("paramInfo") ?: return null
    val params = (inputs.obj()?.get("paramInfo") ?: inputs).items().mapNotNull(::readParam)
    return BillerInfo(biller, params)
}

private fun readParam(o: JsonObject): BillerParam? {
    val name = o.str("paramName", "name") ?: return null
    // "visibility": "false" marks a field the switch fills itself.
    if (o.str("visibility")?.equals("false", true) == true) return null
    val type = o.str("dataType").orEmpty().uppercase()
    val pattern = o.str("regEx", "regex")?.let { runCatching { Regex(it) }.getOrNull() }
    return BillerParam(
        name = name,
        numeric = type == "NUMERIC",
        optional = o.str("isOptional")?.equals("true", true) == true,
        minLength = o.str("minLength")?.toIntOrNull()?.takeIf { it > 0 },
        maxLength = o.str("maxLength")?.toIntOrNull()?.takeIf { it > 0 },
        pattern = pattern,
        choices = o.str("values")?.split(',', '|')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
    )
}

/** Paise ("52345") → rupees (523.45). */
internal fun paise(raw: String?): BigDecimal? = raw?.trim()?.replace(",", "")?.toBigDecimalOrNull()?.divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

/**
 * fetch-bill: `{refid, xml, billData: {billerResponse: {customerName, billAmount (paise), billNumber, billDate, dueDate,
 * billPeriod, amountOptions}, additionalInfo: {info}}}`. Without a refid and xml the bill can't be paid, so it's null.
 */
internal fun readBill(e: JsonElement): FetchedBill? {
    val top = e.withKey("refid") ?: e.withKey("requestId") ?: return null
    val requestId = top.str("refid", "requestId") ?: return null
    val xml = top.str("xml", "billPaymentRequestxml") ?: return null
    val r = e.withKey("billerResponse")?.get("billerResponse").obj() ?: e.withKey("billAmount") ?: return null
    val amount = paise(r.str("billAmount")) ?: return null
    val options = r["amountOptions"].obj()?.get("option").items().mapNotNull { o ->
        val label = o.str("amountName") ?: return@mapNotNull null
        paise(o.str("amountValue"))?.takeIf { it.signum() > 0 }?.let { label to it }
    }
    val info = e.withKey("additionalInfo")?.get("additionalInfo").obj()?.get("info").items().mapNotNull { o ->
        val k = o.str("infoName") ?: return@mapNotNull null
        o.str("infoValue")?.let { k to it }
    }
    return FetchedBill(
        requestId = requestId,
        xml = xml,
        customerName = r.str("customerName"),
        billNumber = r.str("billNumber"),
        billDate = r.str("billDate"),
        dueDate = r.str("dueDate"),
        billPeriod = r.str("billPeriod"),
        amount = amount,
        options = options,
        details = info.take(MAX_DETAILS),
    )
}

private const val MAX_DETAILS = 8

/** pay-bill / check-status: `{status, txnstatus, ref_id, apiTrnID, message, updated_user_balance}`, maybe nested. */
internal data class PayAnswer(val status: String?, val refId: String?, val bbpsTxnId: String?, val message: String?, val balanceAfter: BigDecimal?)

internal fun readPay(e: JsonElement): PayAnswer {
    val o = e.withKey("txnstatus") ?: e.withKey("ref_id") ?: e.withKey("apiTrnID") ?: e.obj() ?: JsonObject(emptyMap())
    return PayAnswer(
        status = o.str("txnstatus", "txnStatus", "tranStatus"),
        refId = o.str("ref_id", "refId", "refid"),
        bbpsTxnId = o.str("apiTrnID", "resApiTxnId", "txnReferenceId"),
        message = o.str("message", "msg"),
        balanceAfter = o.str("updated_user_balance")?.toBigDecimalOrNull()?.setScale(2, RoundingMode.DOWN), // never shows more than there is
    )
}

/** An answer whose inner `success` / `status` is false: the server's message as the failure. */
internal fun innerRefusal(e: JsonElement?): String? {
    val o = e.obj() ?: return null
    val flag = (o["success"] as? JsonPrimitive ?: o["status"] as? JsonPrimitive) ?: return null
    val ok = flag.booleanOrNull ?: when (flag.contentOrNull?.trim()?.lowercase()) {
        "1", "true", "success" -> true
        "0", "false", "fail", "failed", "failure", "error" -> false
        else -> null
    }
    return if (ok == false) (o.str("message", "msg") ?: "The request was not completed.") else null
}
