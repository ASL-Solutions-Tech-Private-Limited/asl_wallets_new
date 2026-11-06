package com.asl.wallet.feature.dmt.domain

import com.asl.wallet.core.common.validation.Validators
import java.math.BigDecimal

/** What `check-remitter` said about a customer's mobile number (`statusCheck` 0 / 1 / 2). */
sealed interface CustomerLookup {
    /** "0": never registered here. */
    data object NotRegistered : CustomerLookup
    /** "1": registered but not verified; the server has sent the customer an OTP. */
    data object NeedsOtp : CustomerLookup
    /** "2": verified, ready to send. */
    data class Found(val customer: Customer) : CustomerLookup
}

data class Customer(
    val mobile: String,
    val name: String,
    /** Monthly limit and what's used, when the server sends them. */
    val totalLimit: BigDecimal?,
    val usedLimit: BigDecimal?,
) {
    val leftLimit: BigDecimal? get() = if (totalLimit != null && usedLimit != null) (totalLimit - usedLimit).max(BigDecimal.ZERO) else null
}

enum class Gender(val api: String) { Male("Male"), Female("Female"), Other("Other") }

/** The OTP purpose tags the DMT backend expects in `otpReference`. */
enum class OtpPurpose(val api: String) { Customer("remVerification"), Recipient("benVerification") }

data class Recipient(
    /** Sent as `beneficiaryId` in money-transfer. */
    val id: String,
    val name: String,
    val bankName: String,
    val accountNumber: String,
    val ifsc: String,
    val mobile: String?,
    val verified: Boolean,
) {
    val last4: String get() = accountNumber.takeLast(4)
}

data class DmtBank(val id: String, val name: String, val ifscSample: String?)

enum class TransferMode(val api: String) { Imps("IMPS"), Neft("NEFT") }

data class Charges(val charge: BigDecimal, val total: BigDecimal)

data class DmtReceipt(
    val customerName: String,
    val customerMobile: String,
    val recipient: String,
    val bankName: String,
    val last4: String,
    val mode: String,
    val amount: BigDecimal,
    val charge: BigDecimal?,
    /** The backend may split a big amount; every bank ref it returns is listed. */
    val bankRefs: List<String>,
    val txnId: String?,
    val time: String,
)

sealed interface DmtOutcome {
    data class Sent(val receipt: DmtReceipt) : DmtOutcome
    /** The bank hasn't confirmed, or the network dropped after sending. Never send again. */
    data class Pending(val receipt: DmtReceipt) : DmtOutcome
    data class Failed(val receipt: DmtReceipt, val reason: String?) : DmtOutcome
    /** The OTP was wrong: nothing moved, the retailer can type it again. */
    data class WrongOtp(val reason: String?) : DmtOutcome
    /** The server refused the request (4xx, e.g. "Minimum transfer amount is ₹10"): nothing moved. */
    data class Refused(val reason: String?) : DmtOutcome
}

object DmtStatus {
    private val OK = setOf("SUCCESS", "SUCCESSFUL", "COMPLETED", "DONE", "TRANSFERRED")
    private val BAD = setOf("FAILED", "FAILURE", "FAIL", "REJECTED", "DECLINED", "REFUNDED", "REVERSED", "ROLLBACK")
    /** Only a clear success is success; anything unknown is Pending. */
    fun of(raw: String?): Int = when (raw?.trim()?.uppercase()?.replace(" ", "")) { in OK -> 1; in BAD -> -1; else -> 0 }
}

data class RecipientForm(
    val bank: DmtBank? = null,
    val account: String = "",
    val confirm: String = "",
    val ifsc: String = "",
    val name: String = "",
    val mobile: String = "",
    /** Name confirmed by the bank (optional ₹ 3 check). */
    val bankName: String? = null,
) {
    enum class Field { Bank, Account, Confirm, Ifsc, Name, Mobile }

    val verified: Boolean get() = bankName != null && bankName.equals(name.trim(), ignoreCase = true)

    fun problems(): Set<Field> = buildSet {
        if (bank == null) add(Field.Bank)
        if (!Validators.isAccountNumber(account)) add(Field.Account)
        if (confirm != account || confirm.isEmpty()) add(Field.Confirm)
        if (!Validators.isIfsc(ifsc)) add(Field.Ifsc)
        if (!NAME.matches(name.trim())) add(Field.Name)
        if (!Validators.isMobile(mobile)) add(Field.Mobile)
    }

    /** Fields the paid name check needs. */
    fun canCheckName(): Boolean = bank != null && Validators.isAccountNumber(account) && confirm == account && Validators.isIfsc(ifsc) && Validators.isMobile(mobile)

    companion object { val NAME = Regex("^[A-Za-z][A-Za-z .']{1,99}$") }
}

object DmtRules {
    /** The server's minimum (UAT 5 Nov: 422 "Minimum transfer amount is ₹10"). */
    const val MIN_AMOUNT = 10
    const val MAX_AMOUNT = 2_00_000
    val NAME_CHECK_FEE = BigDecimal(3)
    private val CUSTOMER_NAME = Regex("^[A-Za-z][A-Za-z .']{1,59}$")

    fun isCustomerName(s: String) = CUSTOMER_NAME.matches(s.trim())

    /** Whole rupees. The backend splits large amounts into bank-sized parts (user, 5 Nov 2026); the app never splits. */
    fun amountOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_AMOUNT..MAX_AMOUNT }

    fun cleanMobile(raw: String): String {
        val d = raw.filter(Char::isDigit)
        return (if (d.length > 10 && (d.startsWith("91") || d.startsWith("0"))) d.takeLast(10) else d).take(10)
    }

    /** Recipient search by name, bank or last digits. */
    fun search(all: List<Recipient>, q: String): List<Recipient> {
        val t = q.trim()
        if (t.isEmpty()) return all
        return all.filter { it.name.contains(t, true) || it.bankName.contains(t, true) || (t.all(Char::isDigit) && it.accountNumber.endsWith(t)) }
    }

    /**
     * Typed name vs the bank's name: different when the first names differ, since the first name identifies the person
     * ("Rakesh Kumar" vs "NIKHIL KUMAR" → different; "Nikhil" vs "NIKHIL KUMAR" → same).
     */
    fun namesDiffer(typed: String, bank: String): Boolean {
        fun first(s: String) = s.uppercase().split(Regex("[^A-Z]+")).firstOrNull { it.isNotEmpty() }
        val a = first(typed); val b = first(bank)
        return a != null && b != null && a != b
    }
}
