package com.asl.wallet.feature.recharge.domain

import com.asl.wallet.core.common.validation.Validators
import java.math.BigDecimal

/** `rtype` on the server: 1 = mobile prepaid, 2 = DTH (confirmed by the user, 4 Nov 2026). No postpaid. */
enum class RechargeType(val rtype: Int) { Mobile(1), Dth(2) }

/** An operator as `do-recharge` needs it: [code] goes in `rechopt`. */
data class Operator(val code: String, val name: String)

data class OperatorLists(val mobile: List<Operator>, val dth: List<Operator>)

/** What `fetch-operator-circle` found for a number. The circle is for information only: plans are per operator. */
data class DetectedOperator(val operator: Operator, val circle: String?)

data class Plan(val amount: Int, val validity: String, val description: String, val category: String)

data class PlanGroup(val name: String, val plans: List<Plan>)

data class DthCustomer(
    val name: String?,
    val pack: String?,
    val status: String?,
    val balance: BigDecimal?,
    val monthly: BigDecimal?,
    val nextRecharge: String?,
)

/** One row of the recharge report, for "Recent" and "repeat". */
data class RecentRecharge(val number: String, val operator: String, val operatorCode: String?, val amount: BigDecimal, val status: RechargeStatus, val date: String, val time: String) {
    val type: RechargeType get() = if (Validators.isMobile(number)) RechargeType.Mobile else RechargeType.Dth
}

enum class RechargeStatus { Success, Pending, Failed }

object RechargeStatuses {
    private val OK = setOf("SUCCESS", "SUCCESSFUL", "COMPLETED", "DONE")
    private val BAD = setOf("FAILURE", "FAILED", "FAIL", "REFUND", "REFUNDED", "REVERSED", "REJECTED", "DECLINED", "LOGINFAILED")

    /** Anything not clearly success or failure is Pending: never "Success" by guess. */
    fun of(raw: String?): RechargeStatus = when (raw?.trim()?.uppercase()?.replace(" ", "")) {
        in OK -> RechargeStatus.Success
        in BAD -> RechargeStatus.Failed
        else -> RechargeStatus.Pending
    }
}

data class RechargeReceipt(
    val type: RechargeType,
    val number: String,
    val operator: String,
    val amount: BigDecimal,
    val planLine: String?,
    val customer: String?,
    val operatorRef: String?,
    val txnId: String?,
    val time: String,
)

sealed interface RechargeOutcome {
    data class Done(val receipt: RechargeReceipt) : RechargeOutcome
    /** The operator hasn't confirmed, or the network dropped after sending. Never send again. */
    data class Pending(val receipt: RechargeReceipt) : RechargeOutcome
    /** [debited] is false when the server refused before taking money (a 4xx), true for an operator decline. */
    data class Failed(val receipt: RechargeReceipt, val reason: String?, val debited: Boolean = true) : RechargeOutcome
}

object RechargeRules {
    const val MIN_AMOUNT = 1
    const val MAX_AMOUNT = 50_000
    /** The server wants at least 10 characters (UAT 5 Nov); Tata Play / Airtel use 10, Dish / d2h / Sun 11. */
    private val SUBSCRIBER = Regex("^[A-Za-z0-9]{10,20}$")

    fun isNumberOk(type: RechargeType, number: String): Boolean = when (type) {
        RechargeType.Mobile -> Validators.isMobile(number)
        RechargeType.Dth -> SUBSCRIBER.matches(number.trim())
    }

    /** Recharges are whole rupees. */
    fun amountOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_AMOUNT..MAX_AMOUNT }

    /** Mobile numbers typed or picked from contacts: "+91 97987-43077" → "9798743077". */
    fun cleanMobile(raw: String): String {
        val d = raw.filter(Char::isDigit)
        return (if (d.length > 10 && (d.startsWith("91") || d.startsWith("0"))) d.takeLast(10) else d).take(10)
    }

    /** Groups plans by the name people read (`type`), joining server categories with the same name, cheapest first. */
    fun group(plans: List<Plan>): List<PlanGroup> = plans
        .groupBy { normalise(it.category) }
        .map { (_, list) -> PlanGroup(list.first().category, list.sortedBy { it.amount }.distinctBy { it.amount to it.validity }) }

    private fun normalise(s: String) = s.lowercase().filter(Char::isLetterOrDigit).removeSuffix("plans")

    /** Search by amount ("349") or words in the benefits ("data", "hotstar"). */
    fun search(plans: List<Plan>, q: String): List<Plan> {
        val t = q.trim()
        if (t.isEmpty()) return plans
        t.toIntOrNull()?.let { a -> return plans.filter { it.amount.toString().startsWith(t) || it.amount == a } }
        val words = t.lowercase().split(' ').filter { it.isNotBlank() }
        return plans.filter { p -> words.all { w -> p.description.lowercase().contains(w) || p.category.lowercase().contains(w) || p.validity.lowercase().contains(w) } }
    }

    /** Plan text from the operator is shown as plain text: links are cut so nothing looks tappable. */
    fun cleanText(s: String): String = s
        .replace(Regex("""https?://\S+"""), "")
        .replace(Regex("""\s*\n\s*"""), "\n")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()

    /** "28Days" → "28 days", "3days" → "3 days", "NA" stays. */
    fun cleanValidity(s: String): String = s.trim().replace(Regex("""(?i)^(\d+)\s*(day|days)$""")) { "${it.groupValues[1]} days" }
}
