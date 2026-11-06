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
