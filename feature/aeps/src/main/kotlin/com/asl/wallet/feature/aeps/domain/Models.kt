package com.asl.wallet.feature.aeps.domain

import java.math.BigDecimal

/** AEPS bank partner. Ids match the backend (`aeps/init` providerInfo). */
enum class AepsProvider(val id: Int, val displayName: String) {
    /** InstantPay. Backend transactions not implemented yet ("PROV001"). Only Yes Bank offers cash deposit. */
    YesBank(1, "Yes Bank"),
    /** FingPay. Fully implemented on the new backend. */
    Kotak(2, "Kotak Bank");

    /** Cash deposit is offered on both banks (user, 10 Nov 2026); the server answers if a bank can't take it. */
    val supportsDeposit: Boolean get() = true
    val needsBankVerification: Boolean get() = this == Kotak
    val needsBiometricKyc: Boolean get() = this == YesBank

    companion object { fun of(id: Int?): AepsProvider? = entries.firstOrNull { it.id == id } }
}

data class ProviderInfo(val provider: AepsProvider, val active: Boolean)
data class CustomerBank(val iin: String, val name: String)

/** Biometric scanner app. [packageName] is the RD service the capture intent is sent to. */
data class RdDevice(val id: String, val name: String, val packageName: String, val isFace: Boolean)

data class AepsConfig(
    val providers: List<ProviderInfo>,
    val banks: List<CustomerBank>,
    val devices: List<RdDevice>,
    val quickAmounts: List<Long>,
)

/** Retailer's prefill data from the bank (`bcAgentData`). Never persisted. */
data class AgentPrefill(
    val firstName: String = "", val lastName: String = "", val email: String = "", val phone: String = "",
    val address: String = "", val city: String = "", val district: String = "", val pincode: String = "",
    val bankName: String = "", val ifsc: String = "", val accountNo: String = "", val holderName: String = "",
    val shopName: String = "", val shopPan: String = "", val shopAddress: String = "", val shopCity: String = "",
    val shopDistrict: String = "", val shopState: String = "", val shopPincode: String = "",
)

data class AgentStatus(
    val ekycDone: Boolean,
    val bankEkycDone: Boolean,
    val bioKycDone: Boolean,
    val dailyLoginDone: Boolean,
    val outletId: String,
    val wadh: String?,
    val referenceKey: String?,
    val prefill: AgentPrefill,
)

enum class NextStep { Onboarding, BankVerification, BiometricKyc, DailyLogin, Ready }

/** Same order as the old app and the backend's sequential check. */
fun AgentStatus.nextStep(provider: AepsProvider): NextStep = when {
    !ekycDone -> NextStep.Onboarding
    provider.needsBankVerification && !bankEkycDone -> NextStep.BankVerification
    provider.needsBiometricKyc && !bioKycDone -> NextStep.BiometricKyc
    !dailyLoginDone -> NextStep.DailyLogin
    else -> NextStep.Ready
}

/** Aadhaar Pay (ADHPAY) is hidden for now (user, 4 Nov 2026). */
enum class TxnType(val code: String, val needsAmount: Boolean) {
    Withdraw("CW", true), Balance("BE", false), Statement("MS", false), Deposit("CD", true);

    /** Above ₹5,000 the bank sends the customer an OTP first (cash withdrawal only here). */
    fun needsCustomerOtp(amount: BigDecimal?): Boolean = this == Withdraw && amount != null && amount > OTP_THRESHOLD

    companion object {
        val OTP_THRESHOLD: BigDecimal = BigDecimal(5000)
        fun forProvider(p: AepsProvider) = if (p.supportsDeposit) entries.toList() else entries.filter { it != Deposit }
    }
}

enum class CaptureType { FINGER, FACE }

data class StatementLine(val date: String, val narration: String, val isCredit: Boolean, val amount: String)

data class AepsReceipt(
    val type: TxnType,
    val provider: AepsProvider,
    val amount: BigDecimal?,
    val bankBalance: String?,
    val bankName: String,
    val bankRef: String,
    val customerAadhaarMasked: String,
    val customerMobileMasked: String,
    val time: String,
    val statement: List<StatementLine> = emptyList(),
    val message: String? = null,
)

/** Outcome of a money call, kept separate so a timeout is never mistaken for a failure. */
sealed interface TxnOutcome {
    data class Success(val receipt: AepsReceipt) : TxnOutcome
    data class Failed(val reason: String?) : TxnOutcome
    /** Bank didn't answer in time. Never resend; check status instead. */
    data object Pending : TxnOutcome
    /** Backend says the provider isn't implemented yet (Yes Bank: PROV001). */
    data object ProviderUnavailable : TxnOutcome
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    val lat: String get() = "%.7f".format(java.util.Locale.US, latitude)
    val lon: String get() = "%.7f".format(java.util.Locale.US, longitude)
}
