package com.asl.wallet.feature.wallet.domain

import com.asl.wallet.core.common.validation.Validators
import java.math.BigDecimal
import java.util.Calendar

// ---------------------------------------------------------------- 4.1 Wallet

data class WalletOverview(
    val balance: BigDecimal,
    val eTopup: BigDecimal,
    val sales: BigDecimal,
    val commission: BigDecimal,
)

data class WalletEntry(val id: Long, val title: String, val date: String, val time: String, val amount: BigDecimal) {
    val isCredit: Boolean get() = amount.signum() >= 0
}

// ---------------------------------------------------------------- limits (server values, checked in the app too)

enum class AmountProblem { Empty, BelowMin, AboveMax, OverBalance }

data class Limits(val min: BigDecimal, val max: BigDecimal) {
    /** null = the amount can be sent. The server checks again; this is only instant feedback. */
    fun check(amount: BigDecimal?, balance: BigDecimal? = null): AmountProblem? = when {
        amount == null || amount.signum() <= 0 -> AmountProblem.Empty
        amount < min -> AmountProblem.BelowMin
        amount > max -> AmountProblem.AboveMax
        balance != null && amount > balance -> AmountProblem.OverBalance
        else -> null
    }

    /** The three largest round amounts inside the limits (₹ 1 to ₹ 2,000 → 500, 1,000, 2,000). */
    fun quickAmounts(): List<Long> {
        val lo = min.toLong(); val hi = max.toLong()
        val round = ROUND.filter { it in lo..hi }
        return (if (round.isEmpty()) listOf(lo, hi) else round.takeLast(3)).distinct()
    }

    companion object { private val ROUND = listOf(10L, 50L, 100L, 200L, 500L, 1_000L, 2_000L, 5_000L, 10_000L, 25_000L, 50_000L, 1_00_000L) }
}

// ---------------------------------------------------------------- 4.3–4.11 Add money (PayU CheckoutPro)

data class AddMoneyInfo(val balance: BigDecimal?, val limits: Limits, val modes: List<String>)

/**
 * What the server returns for one payment: the ASL txn id and every field the PayU SDK needs, hashes included.
 * The PayU salt never reaches the app. [params] are passed to the SDK as they are.
 */
data class PayUOrder(val txnId: String, val amount: BigDecimal, val params: Map<String, String>)

sealed interface AddMoneyResult {
    val txnId: String
    data class Success(override val txnId: String, val amount: BigDecimal?, val mode: String?, val payuRef: String?) : AddMoneyResult
    data class Pending(override val txnId: String, val amount: BigDecimal?) : AddMoneyResult
    data class Failed(override val txnId: String, val amount: BigDecimal?, val reason: String?) : AddMoneyResult
}

object AddMoneyStatus {
    private val OK = setOf("SUCCESS", "SUCCESSFUL", "COMPLETED", "CAPTURED", "PAID", "CREDITED")
    private val FAILED = setOf("FAILED", "FAILURE", "CANCELLED", "CANCELED", "USER_CANCELLED", "USERCANCELLED", "EXPIRED", "DECLINED", "BOUNCED", "DROPPED", "REJECTED")

    /** The wallet counts as credited only on a server SUCCESS. Anything unknown is Pending, never Success. */
    fun of(status: String?, complete: Boolean?, txnId: String, amount: BigDecimal?, mode: String?, payuRef: String?, reason: String?): AddMoneyResult {
        val s = status?.trim()?.uppercase()?.replace(" ", "_")
        return when {
            s in OK -> AddMoneyResult.Success(txnId, amount, mode, payuRef)
            s in FAILED -> AddMoneyResult.Failed(txnId, amount, reason)
            else -> AddMoneyResult.Pending(txnId, amount)
        }.let { if (it is AddMoneyResult.Success && complete == false) AddMoneyResult.Pending(txnId, amount) else it }
    }
}

// ---------------------------------------------------------------- 4.12–4.19 Fund request (always to Admin)

data class CompanyBank(val id: Long, val name: String, val accountNumber: String, val ifsc: String?) {
    val last4: String get() = accountNumber.takeLast(4)
}

enum class FundMode(val api: String) {
    Cash("CASH"), Imps("IMPS"), Neft("NEFT"), Upi("UPI"), Others("OTHERS");
    companion object { fun of(v: String?) = entries.firstOrNull { it.api.equals(v?.trim(), ignoreCase = true) } }
}

data class FundRequestSetup(val adminId: String, val banks: List<CompanyBank>, val modes: List<FundMode>)

enum class FundStatus { Pending, Approved, Declined }

data class FundRequestItem(
    val id: Long,
    val ref: String,
    val amount: BigDecimal,
    val mode: FundMode?,
    val bankId: Long?,
    val bankName: String?,
    val accountLast4: String?,
    val utr: String,
    /** As the server sends it (YYYY-MM-DD or DD-MM-YYYY). */
    val depositDate: String,
    val remarks: String?,
    val adminRemarks: String?,
    val sentDate: String,
    val sentTime: String,
    val status: FundStatus,
) {
    val editable: Boolean get() = status == FundStatus.Pending
}

data class FundSummary(val pendingCount: Int, val pendingAmount: BigDecimal)

data class FundRequestPage(val items: List<FundRequestItem>, val summary: FundSummary?, val canLoadMore: Boolean)

data class FundRequestForm(
    val bank: CompanyBank? = null,
    val amountText: String = "",
    val mode: FundMode? = null,
    val utr: String = "",
    val date: Day? = null,
    val remarks: String = "",
) {
    enum class Field { Bank, Amount, Mode, Utr, Date, Remarks }

    /** Fields with a problem. [today] is passed in so tests don't depend on the clock. */
    fun problems(today: Day): Set<Field> = buildSet {
        if (bank == null) add(Field.Bank)
        val a = amountText.toBigDecimalOrNull()
        if (a == null || a.signum() <= 0 || a > MAX_AMOUNT) add(Field.Amount)
        if (mode == null) add(Field.Mode)
        if (!UTR.matches(utr.trim())) add(Field.Utr)
        if (date == null || date > today || date.epochDay < today.epochDay - MAX_AGE_DAYS) add(Field.Date)
        if (remarks.length > 255) add(Field.Remarks)
    }

    companion object {
        val MAX_AMOUNT = BigDecimal("10000000")
        const val MAX_AGE_DAYS = 90L
        /** Bank references: letters, digits and - / only, 4 to 40 long. */
        val UTR = Regex("^[A-Za-z0-9/\\-]{4,40}$")
    }
}

// ---------------------------------------------------------------- 4.20–4.26 Send to bank

data class SendAccount(val id: String, val bankName: String, val accountLast4: String, val holder: String, val ifsc: String)

data class SendMoneySetup(
    /** Verified accounts only: unverified ones can never receive money. */
    val accounts: List<SendAccount>,
    val balance: BigDecimal?,
    val limits: Limits,
    val modes: List<String>,
    val notes: List<String>,
    val otpTo: String?,
)

data class SendReceipt(
    val amount: BigDecimal,
    val charge: BigDecimal?,
    val totalDebited: BigDecimal?,
    val holder: String,
    val bankName: String,
    val accountLast4: String,
    val mode: String,
    val bankRef: String?,
    val txnId: String?,
    val time: String,
)

sealed interface SendOutcome {
    data class Sent(val receipt: SendReceipt) : SendOutcome
    /** The bank hasn't confirmed, or the network dropped after sending. Never send again. */
    data class Pending(val receipt: SendReceipt?) : SendOutcome
    data class Failed(val reason: String?) : SendOutcome
}

// ---------------------------------------------------------------- 4.27–4.32 Bank accounts

/** `acctype` on the server: 1 = the retailer's own account (PRIMARY), 2 = a relative's account (RELATIVE). */
enum class AccountOwner(val api: String) { Self("1"), Relative("2") }

data class BankAccount(
    val id: Long,
    val bankName: String,
    val accountLast4: String,
    val holder: String,
    val ifsc: String,
    val owner: AccountOwner,
    val verified: Boolean,
)

data class BankOption(val id: String, val name: String)

data class AddBankForm(
    val bank: BankOption? = null,
    val account: String = "",
    val confirm: String = "",
    val ifsc: String = "",
    val holder: String = "",
    val owner: AccountOwner = AccountOwner.Self,
    val panPhoto: Int = 0,
    val passbookPhoto: Int = 0,
) {
    enum class Field { Bank, Account, Confirm, Ifsc, Holder, PanPhoto, PassbookPhoto }

    val confirmMatches: Boolean get() = confirm.isNotEmpty() && confirm == account

    fun problems(): Set<Field> = buildSet {
        if (bank == null) add(Field.Bank)
        if (!Validators.isAccountNumber(account)) add(Field.Account)
        if (!confirmMatches) add(Field.Confirm)
        if (!Validators.isIfsc(ifsc)) add(Field.Ifsc)
        if (!HOLDER.matches(holder.trim())) add(Field.Holder)
        if (panPhoto <= 0) add(Field.PanPhoto)
        if (passbookPhoto <= 0) add(Field.PassbookPhoto)
    }

    companion object {
        val HOLDER = Regex("^[A-Za-z][A-Za-z .']{1,99}$")
    }
}

/** Bank search: names that start with the query first, then names that contain it. */
fun searchBanks(all: List<BankOption>, q: String): List<BankOption> {
    val t = q.trim()
    if (t.isEmpty()) return all
    val starts = all.filter { it.name.startsWith(t, ignoreCase = true) }
    val contains = all.filter { !it.name.startsWith(t, ignoreCase = true) && it.name.contains(t, ignoreCase = true) }
    return starts + contains
}

/** A calendar date without time (minSdk 24 has no java.time). Months are 1–12. */
data class Day(val year: Int, val month: Int, val day: Int) : Comparable<Day> {
    /** Days since 1970-01-01 (proleptic Gregorian), for comparisons only. */
    val epochDay: Long get() {
        val y = if (month <= 2) year - 1 else year
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468
    }

    override fun compareTo(other: Day): Int = epochDay.compareTo(other.epochDay)

    /** "2026-11-04", the format the server takes. */
    fun iso(): String = "%04d-%02d-%02d".format(year, month, day)

    companion object {
        fun today(cal: Calendar = Calendar.getInstance()) = Day(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

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

        fun ofEpochMillisUtc(ms: Long): Day {
            val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
            return today(c)
        }
    }
}
