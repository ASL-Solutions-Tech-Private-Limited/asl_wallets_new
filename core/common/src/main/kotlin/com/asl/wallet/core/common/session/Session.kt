package com.asl.wallet.core.common.session

import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Access and refresh tokens returned by the auth API. */
data class SessionTokens(val accessToken: String, val refreshToken: String)

/**
 * Secure token storage. Implemented in core:security (encrypted with an Android Keystore key);
 * used by core:network to attach and refresh tokens. Tokens are never logged.
 */
interface TokenStore {
    suspend fun read(): SessionTokens?
    suspend fun save(tokens: SessionTokens)
    suspend fun clear()
    val isSignedIn: Flow<Boolean>
}

/** Why the user was signed out. The UI shows a matching message. */
enum class SignOutReason { UserRequested, SessionExpired, SignedInElsewhere, PasswordChanged }

/**
 * One idempotency key per money action. It's created when the user starts the action (for example,
 * at the Review screen), reused on any retry of the same action, and replaced only when a new
 * action starts. This is what stops double payments.
 */
@JvmInline
value class IdempotencyKey(val value: String) {
    companion object {
        fun new(): IdempotencyKey = IdempotencyKey(UUID.randomUUID().toString())
    }
}
