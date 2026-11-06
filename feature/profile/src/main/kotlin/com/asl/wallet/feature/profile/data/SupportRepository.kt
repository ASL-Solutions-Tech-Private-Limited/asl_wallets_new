package com.asl.wallet.feature.profile.data

import com.asl.wallet.feature.profile.domain.Complaint
import com.asl.wallet.feature.profile.domain.ComplaintDraft
import com.asl.wallet.feature.profile.domain.FeedbackDraft
import com.asl.wallet.feature.profile.domain.SupportResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complaints and feedback. The screens and flow are complete; the merchant API does not exist yet.
 * When it ships, add a Retrofit implementation and bind it in [ProfileModule]: nothing else changes.
 * Wanted: POST merchant/complaints, GET merchant/complaints, POST merchant/feedback (see API_REVIEW_FOR_BACKEND.md).
 */
interface SupportRepository {
    suspend fun raiseComplaint(draft: ComplaintDraft): SupportResult<String>
    suspend fun myComplaints(): SupportResult<List<Complaint>>
    suspend fun sendFeedback(draft: FeedbackDraft): SupportResult<Unit>
}

@Singleton
class PendingSupportRepository @Inject constructor() : SupportRepository {
    override suspend fun raiseComplaint(draft: ComplaintDraft) = SupportResult.ApiNotReady
    override suspend fun myComplaints() = SupportResult.ApiNotReady
    override suspend fun sendFeedback(draft: FeedbackDraft) = SupportResult.ApiNotReady
}

/** ASL's official support contacts, set per build flavour by the app. */
data class SupportContacts(val phone: String, val whatsapp: String)
