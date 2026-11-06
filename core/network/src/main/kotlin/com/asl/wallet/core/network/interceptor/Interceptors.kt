package com.asl.wallet.core.network.interceptor

import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.NetworkConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

object ApiHeaders {
    const val AUTHORIZATION = "Authorization"
    const val REQUEST_ID = "X-Request-Id"
    const val IDEMPOTENCY_KEY = "Idempotency-Key"
    const val APP_VERSION = "X-App-Version"
    const val PLATFORM = "X-Platform"

    /** Marker header for endpoints that must not carry a token (login, OTP, forgot password). Removed before sending. */
    const val NO_AUTH = "X-No-Auth"
    const val NO_AUTH_VALUE = "$NO_AUTH: true"
}

/** Emits when the session ends on its own (refresh failed, revoked). The app listens and goes to Login. */
@Singleton
class SessionEvents @Inject constructor() {
    private val _signOut = MutableSharedFlow<SignOutReason>(extraBufferCapacity = 1)
    val signOut: SharedFlow<SignOutReason> = _signOut.asSharedFlow()
    fun emit(reason: SignOutReason) { _signOut.tryEmit(reason) }
}

/** Adds JSON accept, platform, app version and a unique request ID (for support and log correlation). */
class CommonHeadersInterceptor(private val config: NetworkConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("Accept", "application/json")
            .header(ApiHeaders.PLATFORM, "android")
            .header(ApiHeaders.APP_VERSION, config.appVersion)
            .apply {
                if (chain.request().header(ApiHeaders.REQUEST_ID) == null) {
                    header(ApiHeaders.REQUEST_ID, UUID.randomUUID().toString())
                }
            }
            .build()
        return chain.proceed(req)
    }
}

/** Attaches "Authorization: Bearer <access token>" unless the request is marked [ApiHeaders.NO_AUTH]. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header(ApiHeaders.NO_AUTH) != null) {
            return chain.proceed(original.newBuilder().removeHeader(ApiHeaders.NO_AUTH).build())
        }
        val token = runBlocking { tokenStore.read() }?.accessToken
        val req = if (token.isNullOrBlank()) original
        else original.newBuilder().header(ApiHeaders.AUTHORIZATION, "Bearer $token").build()
        return chain.proceed(req)
    }
}

/**
 * OkHttp's own retry is off (a money POST must never be sent twice). That also stopped it recovering when
 * a pooled keep-alive connection was closed by the server while idle ("unexpected end of stream").
 * This puts back exactly one retry, and only for **GET** (read-only) requests failing on a dead connection.
 * Timeouts, TLS/pin failures, DNS errors and every non-GET request are never retried.
 */
class StaleConnectionRetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        return try {
            chain.proceed(request)
        } catch (e: java.io.IOException) {
            if (!isStaleConnection(e) || chain.call().isCanceled()) throw e
            chain.proceed(request)
        }
    }

    companion object {
        internal fun isStaleConnection(e: java.io.IOException): Boolean = when {
            e is java.net.SocketTimeoutException || e is javax.net.ssl.SSLException || e is java.net.UnknownHostException -> false
            e is java.io.EOFException -> true
            e.message?.contains("unexpected end of stream", ignoreCase = true) == true -> true
            e.javaClass.simpleName == "ConnectionShutdownException" -> true
            e is java.net.SocketException && e.message?.contains("reset", ignoreCase = true) == true -> true
            else -> false
        }
    }
}
