package com.asl.wallet.core.common.session

import kotlinx.coroutines.flow.StateFlow

/** What other features may know about the signed-in user. Provided by feature:auth. */
data class UserSummary(
    val name: String,
    val shopName: String,
    val regno: Long,
    val userType: String,
    val kycDone: Boolean,
) {
    val firstName: String get() = name.substringBefore(' ').ifBlank { name }
    val initials: String get() = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
    val isDistributor: Boolean get() = userType.contains("distributor", ignoreCase = true)
}

interface CurrentUser {
    /** null when signed out. */
    val user: StateFlow<UserSummary?>
    suspend fun logout()
}

/**
 * Each feature that caches user data binds one of these with `@IntoSet`.
 * The session owner calls every cleanup on every sign-out (user, expired, revoked).
 */
fun interface SignOutCleanup {
    suspend fun clear()
}
