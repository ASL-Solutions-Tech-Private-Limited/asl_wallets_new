package com.asl.wallet.feature.bbps.domain

import com.asl.wallet.core.common.session.IdempotencyKey
import java.math.BigDecimal

/** A bill category ("Electricity", "Fastag", "LPG Gas" …). [name] is what the biller list is asked for. */
data class BillCategory(val name: String)

/** One biller (operator) in a category. [code] is its BBPS biller id ("WEST00000WBL75"). */
data class Biller(val code: String, val name: String, val category: String)

/**
 * One customer input the biller asks for. [choices] is a fixed list when the biller gives one.
 * [pattern] is the biller's own rule; an invalid pattern from the server is ignored (never crashes).
 */
data class BillerParam(
    val name: String,
    val numeric: Boolean,
    val optional: Boolean,
    val minLength: Int?,
    val maxLength: Int?,
    val pattern: Regex?,
    val choices: List<String> = emptyList(),
) {
    sealed interface Check {
        data object Ok : Check
        data object Missing : Check
        data object TooShort : Check
        data object Invalid : Check
    }

    fun check(raw: String): Check {
        val v = raw.trim()
        return when {
            v.isEmpty() -> if (optional) Check.Ok else Check.Missing
            minLength != null && v.length < minLength -> Check.TooShort
            maxLength != null && v.length > maxLength -> Check.Invalid
            numeric && !v.all(Char::isDigit) -> Check.Invalid
            pattern != null && !pattern.matches(v) -> Check.Invalid
            choices.isNotEmpty() && v !in choices -> Check.Invalid
            else -> Check.Ok
        }
    }

    /** "Mobile Number", "Registered Mobile No" … : the customer's mobile, sent as Mobile_Number on payment. */
    val isMobile: Boolean get() = name.lowercase().let { "mobile" in it || "phone" in it }
}

/** What the biller needs before fetching a bill. */
data class BillerInfo(val biller: Biller, val params: List<BillerParam>)

/**
 * A fetched bill. [requestId] and [xml] come from fetch-bill and must go back unchanged with the payment.
 * [amount] is in rupees (the biller answers in paise). [details] are extra lines the biller sent (balance etc.).
 */
data class FetchedBill(
    val requestId: String,
    val xml: String,
    val customerName: String?,
    val billNumber: String?,
    val billDate: String?,
    val dueDate: String?,
    val billPeriod: String?,
    val amount: BigDecimal,
    val options: List<Pair<String, BigDecimal>> = emptyList(),
    val details: List<Pair<String, String>> = emptyList(),
)

/** One payment the retailer confirmed. [key] is made once per confirmation and never reused. */
data class BillPayment(
    val biller: Biller,
    val inputs: List<Pair<BillerParam, String>>,
    val bill: FetchedBill,
    val amount: BigDecimal,
    val key: IdempotencyKey,
) {
    /** The first input is the consumer / account number on every biller (ca_number). */
    val consumerNumber: String get() = inputs.firstOrNull()?.second?.trim().orEmpty()
    val customerMobile: String get() = inputs.firstOrNull { it.first.isMobile }?.second?.trim().orEmpty()
}

data class BbpsReceipt(
    val category: String,
    val biller: String,
    /** "Consumer ID" → its value (masked on screen if long and numeric). */
    val consumerLabel: String,
    val consumerNumber: String,
    val customerName: String?,
    val billNumber: String?,
    val amount: BigDecimal,
    /** ASL / BBPS reference (ref_id). */
    val refId: String?,
    /** Bharat Connect transaction id (apiTrnID). */
    val bbpsTxnId: String?,
    val time: String,
)

sealed interface BbpsOutcome {
    val receipt: BbpsReceipt
    data class Paid(override val receipt: BbpsReceipt, val balanceAfter: BigDecimal?) : BbpsOutcome
    /** The biller hasn't confirmed, or the line dropped after sending. Never pay again; check the report. */
    data class Pending(override val receipt: BbpsReceipt) : BbpsOutcome
    /** [debited] false = refused before any money was taken (a 4xx). */
    data class Failed(override val receipt: BbpsReceipt, val reason: String?, val debited: Boolean) : BbpsOutcome
}

object BbpsRules {
    /** The old app's BBPS categories, used when get-biller-category answers an empty list. */
    val DEFAULT_CATEGORIES = listOf(
        "Electricity", "Fastag", "LPG Gas", "Loan Repayment", "Mobile Postpaid", "Water", "Broadband Postpaid",
    ).map(::BillCategory)

    /** The old app let the retailer change the amount only for these (prepaid-style) categories. */
    private val EDITABLE = listOf("dth", "mobile prepaid", "mobile postpaid", "fastag", "recharge")

    /** Amount may be changed when the category allows it or the biller sent no amount. */
    fun amountEditable(category: String, billAmount: BigDecimal): Boolean =
        billAmount.signum() <= 0 || EDITABLE.any { category.lowercase().contains(it) }

    val MIN_AMOUNT: BigDecimal = BigDecimal.ONE
    val MAX_AMOUNT: BigDecimal = BigDecimal(2_00_000)

    /** Rupees with at most 2 decimals, within limits. */
    fun amountOf(text: String): BigDecimal? {
        val v = text.trim().replace(",", "").toBigDecimalOrNull() ?: return null
        if (v.scale() > 2 || v < MIN_AMOUNT || v > MAX_AMOUNT) return null
        return v
    }

    /** Status words from pay-bill / check-status. Anything not clearly success or failure is Pending. */
    enum class Status { Success, Pending, Failed }

    fun statusOf(raw: String?): Status = when (raw?.trim()?.uppercase()?.replace(" ", "")) {
        "SUCCESS", "SUCCESSFUL", "COMPLETED", "PAID", "DONE" -> Status.Success
        "FAILURE", "FAILED", "FAIL", "REJECTED", "DECLINED", "REFUND", "REFUNDED", "REVERSED" -> Status.Failed
        else -> Status.Pending
    }

    /** Biller search on name, case-insensitive. */
    fun search(all: List<Biller>, q: String): List<Biller> {
        val t = q.trim()
        return if (t.isEmpty()) all else all.filter { it.name.contains(t, ignoreCase = true) }
    }
}
