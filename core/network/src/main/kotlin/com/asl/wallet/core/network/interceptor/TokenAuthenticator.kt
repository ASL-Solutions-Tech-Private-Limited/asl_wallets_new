package com.asl.wallet.core.network.interceptor

import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

@Serializable
internal data class RefreshRequest(val refreshToken: String)

@Serializable
internal data class RefreshResponse(val accessToken: String, val refreshToken: String? = null)

/**
 * On a 401, tries ONE refresh with the stored refresh token, then retries the original request once.
 * If the refresh fails, tokens are wiped and [SessionEvents] tells the app to sign out.
 *
 * Calls are serialized so ten parallel 401s cause one refresh, not ten.
 * Money requests are safe to retry here because they carry an Idempotency-Key.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val sessionEvents: SessionEvents,
    private val json: Json,
    private val refreshClient: OkHttpClient,
    private val refreshUrl: HttpUrl,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedRequest = response.request
        if (failedRequest.header(ApiHeaders.AUTHORIZATION) == null) return null // was not an authed call
        if (responseCount(response) >= 2) return signOut()

        synchronized(lock) {
            val current = runBlocking { tokenStore.read() } ?: return signOut()
            val usedToken = failedRequest.header(ApiHeaders.AUTHORIZATION)?.removePrefix("Bearer ")

            // Another thread already refreshed while we waited: just retry with the new token.
            if (usedToken != current.accessToken) {
                return failedRequest.withToken(current.accessToken)
            }

            val refreshed = refresh(current.refreshToken) ?: return signOut()
            val newTokens = SessionTokens(refreshed.accessToken, refreshed.refreshToken ?: current.refreshToken)
            runBlocking { tokenStore.save(newTokens) }
            return failedRequest.withToken(newTokens.accessToken)
        }
    }

    private fun refresh(refreshToken: String): RefreshResponse? = runCatching {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(refreshUrl).post(body).build()
        refreshClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val text = res.body?.string() ?: return null
            val env = json.decodeFromString(ApiEnvelope.serializer(RefreshResponse.serializer()), text)
            if (env.status) env.data else null
        }
    }.getOrNull()

    private fun signOut(): Request? {
        runBlocking { tokenStore.clear() }
        sessionEvents.emit(SignOutReason.SessionExpired)
        return null
    }

    private fun Request.withToken(token: String) =
        newBuilder().header(ApiHeaders.AUTHORIZATION, "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var count = 1
        while (r?.priorResponse != null) { count++; r = r.priorResponse }
        return count
    }
}
