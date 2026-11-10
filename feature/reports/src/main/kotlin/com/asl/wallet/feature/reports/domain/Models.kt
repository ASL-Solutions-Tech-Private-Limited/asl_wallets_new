package com.asl.wallet.feature.reports.domain

import java.math.BigDecimal
import java.util.Calendar

/** A calendar date without time (minSdk 24 has no java.time). Months are 1–12. */
data class Day(val year: Int, val month: Int, val day: Int) : Comparable<Day> {
    private fun cal(): Calendar = Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
    private fun of(c: Calendar) = Day(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    fun plusDays(n: Int): Day = of(cal().apply { add(Calendar.DAY_OF_MONTH, n) })
    /** Same day [n] months earlier; the 31st becomes the last day of a shorter month. */
    fun minusMonths(n: Int): Day = of(cal().apply { add(Calendar.MONTH, -n) })
    fun monthStart(): Day = copy(day = 1)
    fun monthEnd(): Day = of(cal().apply { set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH)) })

    override fun compareTo(other: Day): Int = compareValuesBy(this, other, Day::year, Day::month, Day::day)

    /** "2026-11-06": the format every report API takes. */
    fun iso(): String = "%04d-%02d-%02d".format(year, month, day)

    /** "6 Nov 2026" (English month names are also used on receipts and statements). */
    fun label(): String = "$day ${MONTHS[month - 1]} $year"
    fun shortLabel(): String = "$day ${MONTHS[month - 1]}"

    companion object {
        private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        fun today(cal: Calendar = Calendar.getInstance()) = Day(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

        fun ofEpochMillisUtc(ms: Long): Day {
            val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
            return today(c)
        }

        /** Reads "2026-11-04", "2026-11-04T…", "04-11-2026" or "04/11/2026". */
        fun parse(raw: String?): Day? {
            val t = raw?.trim()?.take(10) ?: return null
            val iso = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(t)
            val dmy = Regex("^(\\d{2})[-/](\\d{2})[-/](\\d{4})$").find(t)
            val d = when {
                iso != null -> iso.groupValues.let { Day(it[1].toInt(), it[2].toInt(), it[3].toInt()) }
                dmy != null -> dmy.groupValues.let { Day(it[3].toInt(), it[2].toInt(), it[1].toInt()) }
                else -> return null
            }
            return d.takeIf { it.month in 1..12 && it.day in 1..31 }
        }
    }
}

enum class Preset { Today, Yesterday, Last7, ThisMonth, LastMonth, Custom }

data class Period(val from: Day, val to: Day, val preset: Preset) {
    fun label(): String = if (from == to) from.label() else if (from.year == to.year) "${from.shortLabel()} – ${to.label()}" else "${from.label()} – ${to.label()}"
}

object Periods {
    /** The APIs (mATM, PAN) refuse longer ranges, so every custom range is capped at this. */
    const val MAX_MONTHS = 3

    fun of(preset: Preset, today: Day): Period = when (preset) {
        Preset.Today -> Period(today, today, preset)
        Preset.Yesterday -> today.plusDays(-1).let { Period(it, it, preset) }
        Preset.Last7 -> Period(today.plusDays(-6), today, preset)
        Preset.ThisMonth, Preset.Custom -> Period(today.monthStart(), today, Preset.ThisMonth)
        Preset.LastMonth -> today.monthStart().plusDays(-1).let { Period(it.monthStart(), it, preset) }
    }

    /** Default for every report (user, 6 Nov 2026): this month. */
    fun default(today: Day) = of(Preset.ThisMonth, today)

    sealed interface Check {
        data object Ok : Check
        data object FromAfterTo : Check
        data object InFuture : Check
        data object TooLong : Check
        /** mATM / PAN: the start can't be more than 3 months back. */
        data object TooOld : Check
    }

    fun check(from: Day, to: Day, today: Day, lookbackLimited: Boolean): Check = when {
        from > to -> Check.FromAfterTo
        to > today -> Check.InFuture
        from < to.minusMonths(MAX_MONTHS) -> Check.TooLong
        lookbackLimited && from < today.minusMonths(MAX_MONTHS) -> Check.TooOld
        else -> Check.Ok
    }
}

enum class RowStatus { Success, Pending, Failed, Refunded }

object Statuses {
    private val OK = setOf("SUCCESS", "SUCCESSFUL", "COMPLETED", "DONE", "APPROVED")
    private val BAD = setOf("FAILURE", "FAILED", "FAIL", "REJECTED", "DECLINED", "LOGINFAILED", "CANCELLED")
    private val BACK = setOf("REFUND", "REFUNDED", "REVERSED", "ROLLBACK")

    /** Anything not clearly success, failure or refund is Pending: never "Success" by guess. */
    fun of(raw: String?, refund: String? = null): RowStatus {
        val s = raw?.trim()?.uppercase()?.replace(" ", "")
        val r = refund?.trim()?.uppercase()?.replace(" ", "")
        return when {
            s in BACK -> RowStatus.Refunded
            s in BAD -> if (r == "REFUNDED") RowStatus.Refunded else RowStatus.Failed
            s in OK -> RowStatus.Success
            else -> RowStatus.Pending
        }
    }
}

/**
 * The service reports. [path] is a fixed constant (never built from input). [statusParam] is null where the API
 * has no status filter; [failedValue] is what that API matches for failures. Complaints: [complainPath] and
 * the API's text limit; the server accepts them only for Success / Pending rows.
 */
enum class ReportKind(
    val path: String,
    val statusParam: String?,
    val failedValue: String?,
    val complainPath: String?,
    val complaintMax: Int,
    /** mATM and PAN refuse a start date more than 3 months back. */
    val lookbackLimited: Boolean = false,
) {
    Recharge("merchant/recharge-report", "status", "FAILED", "merchant/recharge-report/complain", 100),
    Payout("merchant/payout-report", "tranStatus", "FAILED", "merchant/payout-report/complain", 250),
    Aeps("merchant/aeps-report", null, null, "merchant/aeps-report/complain", 250),
    /**
     * No merchant DMT report exists; the user chose the admin report filtered by the retailer's own reg no (6 Nov 2026).
     * The app only ever sends the signed-in retailer's regno (the server must enforce it: reported).
     */
    Dmt("dmt-transaction-report", "tranStatus", "FAIL", null, 0),
    Matm("merchant/matm-report", null, null, "merchant/matm-report/complain", 250, lookbackLimited = true),
    Pan("merchant/pan-report", null, null, "merchant/pan-report/complain", 250, lookbackLimited = true),
    /** Wallet top-ups by PayU. Like the PayU routes it has no `/app` prefix (UAT, 10 Nov 2026), so the path is absolute. */
    AddMoney("/api/v1/merchant/add-money-report", null, null, null, 0),
    ;

    val hasStatusFilter: Boolean get() = statusParam != null

    /** Which API field a search goes in, from what was typed. */
    fun searchParam(q: String): String {
        val t = q.trim()
        val digits = t.all(Char::isDigit)
        return when (this) {
            // ASL txn IDs are 16 characters; mobile and DTH numbers are 10–12.
            Recharge -> if (t.length >= 14) "tranId" else "rechargeNo"
            Payout, Pan -> "servid"
            AddMoney -> "search"
            Aeps, Matm -> if (digits && t.length == 12) "bankRrn" else "tranId"
            Dmt -> when {
                digits && t.length == 10 -> "mobileNumber"
                digits && t.length in 9..18 -> "accountNumber"
                else -> "tranId"
            }
        }
    }

    fun statusValue(s: RowStatus): String? = when (s) {
        RowStatus.Success -> "SUCCESS"
        RowStatus.Pending -> "PENDING"
        RowStatus.Failed -> failedValue
        RowStatus.Refunded -> null
    }
}

/** Labels for detail rows; the UI maps them to strings. [customer] = may be printed on the customer receipt. */
enum class Field(val customer: Boolean = true) {
    Operator, Number, SubscriberId, OperatorRef, Customer, Recipient, Bank, Account, Ifsc, Mode, Charge, Utr, OrderId,
    Type, Aadhaar, Rrn, CustomerBalance, Card, Mobile, Applicant, PanType, PanMode, ApplicationNo, Refund, TxnId, Date,
    Commission(customer = false), Reason(customer = false),
}

data class Complaint(val text: String, val date: String?, val reply: String?)

/** One row of a service report, already masked and stripped of provider logs. */
data class ReportRow(
    val kind: ReportKind,
    /** The transaction id the server knows (servid): used for complaints. */
    val txnId: String,
    val title: String,
    val subtitle: String,
    val amount: BigDecimal?,
    val status: RowStatus,
    val day: Day?,
    val time: String?,
    val fields: List<Pair<Field, String>>,
    val complaint: Complaint?,
    /** Short "AT", "CW" … for the row badge. */
    val badge: String,
    val serverAllowsComplaint: Boolean = true,
) {
    val canComplain: Boolean get() =
        kind.complainPath != null && complaint == null && serverAllowsComplaint && (status == RowStatus.Success || status == RowStatus.Pending)
    val receiptFields: List<Pair<Field, String>> get() = fields.filter { it.first.customer }
}

enum class TotalKind { Amount, Commission, Charges, MoneyIn, MoneyOut, Success, Failed, Pending }

data class Summary(val count: Int?, val totals: List<Pair<TotalKind, BigDecimal>>)

data class ReportPage<T>(val items: List<T>, val summary: Summary?, val canLoadMore: Boolean)

data class LedgerEntry(
    val id: Long,
    val description: String,
    val day: Day?,
    val time: String?,
    val credit: BigDecimal,
    val debit: BigDecimal,
    val opening: BigDecimal?,
    val closing: BigDecimal?,
    val reference: String?,
) {
    val isCredit: Boolean get() = credit.signum() > 0 && debit.signum() == 0
    val amount: BigDecimal get() = if (isCredit) credit else debit
}

enum class LedgerType(val query: String) { All("ALL"), MoneyIn("CREDIT"), MoneyOut("DEBIT") }

object ReportRules {
    /** Search waits until typing stops, and needs a few characters (so a 1-digit query doesn't hit the server). */
    const val MIN_SEARCH = 3
    const val SEARCH_DEBOUNCE_MS = 450L
    const val PAGE = 20
    /** Statement PDF: ledger pages of 200, at most this many entries (3 months for a busy shop). */
    const val STATEMENT_PAGE = 200
    const val STATEMENT_MAX = 3000

    /** Only digits, letters and a few separators go to the server; 40 characters at most. */
    fun cleanSearch(raw: String): String = raw.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.take(40)

    fun cleanComplaint(raw: String, max: Int): String = raw.replace(Regex("[\\u0000-\\u001F<>]"), " ").take(max)

    /** Groups entries by day, keeping server order (newest first). */
    fun <T> byDay(items: List<T>, day: (T) -> Day?): List<Pair<Day?, List<T>>> {
        val out = mutableListOf<Pair<Day?, MutableList<T>>>()
        items.forEach { i -> val d = day(i); if (out.isEmpty() || out.last().first != d) out += d to mutableListOf(i) else out.last().second += i }
        return out
    }
}
