package com.asl.wallet.core.common.session

import kotlinx.coroutines.flow.StateFlow

/** What other features may know about the signed-in user. Provided by feature:auth. */
data class UserSummary(
    val name: String,
    val shopName: String,
    val regno: Long,
    val userType: String,
    val kycDone: Boolean,
    /** Retailer's own phone (from login). Shown on receipts. */
    val phone: String = "",
    /** Retailer's address (loaded from the profile after sign-in). Shown on receipts. */
    val address: String = "",
) {
    val firstName: String get() = name.substringBefore(' ').ifBlank { name }
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
    val isDistributor: Boolean get() = userType.contains("distributor", ignoreCase = true)
}

interface CurrentUser {
    /** null when signed out. */
    val user: StateFlow<UserSummary?>
    /** Fills in the retailer's address once the profile is loaded, so receipts can show it. */
    fun updateAddress(address: String)
    suspend fun logout()
}

/**
 * Each feature that caches user data binds one of these with `@IntoSet`.
 * The session owner calls every cleanup on every sign-out (user, expired, revoked).
 */
fun interface SignOutCleanup {
    suspend fun clear()
}
