package com.asl.wallet.feature.profile.domain

/** Profile as shown: already masked where it is personal data. */
data class ProfileDetails(
    val name: String,
    val initials: String,
    val regno: Long?,
    val shopName: String,
    val mobileMasked: String,
    val emailMasked: String,
    val panMasked: String,
    val address: String,
    val landmark: String,
    val city: String,
    val pincode: String,
)

data class CommissionRow(val label: String, val value: String)
data class CommissionSection(val title: String, val rows: List<CommissionRow>, val isCharge: Boolean = false)
data class Commission(val banking: List<CommissionSection>, val prepaid: List<CommissionSection>)

data class ActiveService(val name: String, val description: String?, val active: Boolean)

/** Where the change-password code went, e.g. "aj•••@gmail.com" by EMAIL. */
data class OtpTarget(val sentTo: String, val channel: String)

// ---- Complaints and feedback: flow built now, API connected later (user, 4 Nov 2026) ----

enum class ComplaintStatus { Open, InProgress, Closed }

data class Complaint(
    val id: String,
    val service: String,
    val summary: String,
    val raisedOn: String,
    val status: ComplaintStatus,
    val timeline: List<Pair<String, String>> = emptyList(),
)

data class ComplaintDraft(
    val service: String,
    val transactionId: String?,
    val description: String,
    val attachment: ByteArray? = null,
) {
    val isValid: Boolean get() = service.isNotBlank() && description.trim().length >= MIN_TEXT
    companion object { const val MIN_TEXT = 10; const val MAX_TEXT = 500 }
}

data class FeedbackDraft(val stars: Int, val tags: Set<String>, val note: String) {
    val isValid: Boolean get() = stars in 1..5
}

/** Result of a complaints/feedback call. [ApiNotReady] until the merchant API exists. */
sealed interface SupportResult<out T> {
    data class Ok<T>(val data: T) : SupportResult<T>
    data object ApiNotReady : SupportResult<Nothing>
    data class Error(val message: String?) : SupportResult<Nothing>
}

// ---- Edit profile (address and shop only) ----

data class ProfileEdit(
    val shopName: String = "",
    val address: String = "",
    val landmark: String = "",
    val city: String = "",
    val pincode: String = "",
) {
    enum class Field { Shop, Address, Landmark, City, Pincode }

    fun problems(): Set<Field> = buildSet {
        if (shopName.trim().length !in 2..120) add(Field.Shop)
        if (address.trim().length !in 5..500) add(Field.Address)
        if (landmark.trim().length > 300) add(Field.Landmark)
        if (!PLACE.matches(city.trim())) add(Field.City)
        if (!PIN.matches(pincode.trim())) add(Field.Pincode)
    }

    companion object {
        val PLACE = Regex("^[A-Za-z][A-Za-z .'-]{1,99}$")
        val PIN = Regex("^[1-9][0-9]{5}$")
        fun of(d: ProfileDetails) = ProfileEdit(d.shopName, d.address, d.landmark, d.city, d.pincode)
    }
}

// ---- All services and their status (Profile → Services, 10 Nov 2026) ----

/** Every service the app offers. [built] = the app has its screens; [always] = every retailer has it (wallet features). */
enum class AppService(val built: Boolean, val always: Boolean = false, val banking: Boolean = true) {
    Aeps(true), MoneyTransfer(true), Payout(true, always = true), MicroAtm(false), UpiTransfer(false), CardToCash(false), AadhaarPay(false),
    MobileRecharge(true, banking = false), DthRecharge(true, banking = false), BillPayment(true, banking = false), Pan(false, banking = false),
}

enum class ServiceState { Active, NotActive, ComingSoon }

data class ServiceEntry(val service: AppService, val state: ServiceState)

object ServiceStatus {
    /** Which of the app's services a server service name switches on ("Recharge" covers mobile and DTH). */
    fun servicesFor(name: String): Set<AppService> {
        val n = " " + name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        fun has(vararg w: String) = w.any { n.contains(it) }
        return when {
            has("micro atm", "matm", "mini atm") -> setOf(AppService.MicroAtm)
            has("aadhaar pay", "aadhar pay") -> setOf(AppService.AadhaarPay)
            has("aeps", "aadhaar enabled") -> setOf(AppService.Aeps)
            has("dmt", "money transfer", "domestic", "remit") -> setOf(AppService.MoneyTransfer)
            has("payout", "settlement") -> setOf(AppService.Payout)
            has("card to cash", "credit card") -> setOf(AppService.CardToCash)
            has(" upi") -> setOf(AppService.UpiTransfer)
            has(" dth ") -> setOf(AppService.DthRecharge)
            has("bbps", "bill", "electric", "fastag", " gas ", " lpg ", "water", "loan", "postpaid") -> setOf(AppService.BillPayment)
            has("recharge", "prepaid", "mobile") -> setOf(AppService.MobileRecharge, AppService.DthRecharge)
            has(" pan ", "nsdl", " uti ") -> setOf(AppService.Pan)
            else -> emptySet()
        }
    }

    /**
     * Every app service with its state, active ones first (in the app's order): Active when switched on for the
     * retailer and built, Coming soon when switched on but not built yet, Not active otherwise (contact support).
     */
    fun entries(active: List<ActiveService>): List<ServiceEntry> {
        val on = active.filter { it.active }.flatMap { servicesFor(it.name) }.toSet()
        return AppService.entries.map { s ->
            val state = when {
                s.always -> ServiceState.Active
                s in on && s.built -> ServiceState.Active
                s in on -> ServiceState.ComingSoon
                else -> ServiceState.NotActive
            }
            ServiceEntry(s, state)
        }.sortedBy { it.state.ordinal }
    }
}
