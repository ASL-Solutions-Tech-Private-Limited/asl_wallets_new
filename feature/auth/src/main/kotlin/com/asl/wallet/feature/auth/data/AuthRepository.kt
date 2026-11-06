package com.asl.wallet.feature.auth.data

import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.feature.auth.domain.ActionResult
import com.asl.wallet.feature.auth.domain.AuthError
import com.asl.wallet.feature.auth.domain.LoginId
import com.asl.wallet.feature.auth.domain.LoginOutcome
import com.asl.wallet.feature.auth.domain.User
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    suspend fun login(id: LoginId, password: String): LoginOutcome
    /** Needs the pending login from [login] (the API asks for the password again). */
    suspend fun verifyOtp(otp: String): LoginOutcome
    suspend fun resendOtp(): ActionResult
    fun hasPendingOtp(): Boolean
    fun clearPending()
    suspend fun forgotPassword(mobile: String): ActionResult
    suspend fun resetPassword(mobile: String, otp: String, newPassword: String): ActionResult
    /** Best effort: tells the server to revoke the refresh token. Local data is cleared by SessionManager. */
    suspend fun logout()
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val json: Json,
    private val tokenStore: TokenStore,
    private val local: AuthLocalStore,
) : AuthRepository {

    /**
     * The verify-otp and resend-otp APIs need the password again, so it is kept in memory only,
     * for at most [PENDING_TTL_MS], and wiped on success, failure to resume, or [clearPending].
     * It is never written to disk or logged. (Backend change requested: otpSessionToken.)
     */
    private class Pending(val id: LoginId, val password: CharArray, val createdAt: Long)
    @Volatile private var pending: Pending? = null
    internal var now: () -> Long = System::currentTimeMillis

    override suspend fun login(id: LoginId, password: String): LoginOutcome {
        val req = when (id) {
            is LoginId.Mobile -> LoginRequest(phone = id.raw, password = password)
            is LoginId.Email -> LoginRequest(email = id.raw, password = password)
        }
        return when (val r = safeApiCall(json, authenticated = false) { api.login(req) }) {
            is NetworkResult.Success -> {
                local.saveLastLoginId(id.raw)
                if (r.data.requiresOtp || r.data.accessToken == null) {
                    pending = Pending(id, password.toCharArray(), now())
                    LoginOutcome.OtpRequired
                } else {
                    completeSignIn(r.data)
                }
            }
            is NetworkResult.Failure -> r.toLoginFailure()
        }
    }

    override suspend fun verifyOtp(otp: String): LoginOutcome {
        val p = livePending() ?: return LoginOutcome.Failed(AuthError.OtpSessionExpired)
        val req = VerifyOtpRequest(p.id.raw, String(p.password), otp)
        return when (val r = safeApiCall(json, authenticated = false) { api.verifyOtp(req) }) {
            is NetworkResult.Success -> {
                if (r.data.accessToken == null) LoginOutcome.Failed(AuthError.Unknown)
                else completeSignIn(r.data).also { clearPending() }
            }
            is NetworkResult.Failure -> r.toLoginFailure()
        }
    }

    override suspend fun resendOtp(): ActionResult {
        val p = livePending() ?: return ActionResult.Failed(AuthError.OtpSessionExpired)
        return safeApiCall(json, allowEmptyData = true, authenticated = false) {
            api.resendOtp(ResendOtpRequest(p.id.raw, String(p.password)))
        }.toAction()
    }

    override fun hasPendingOtp() = livePending() != null

    override fun clearPending() {
        pending?.password?.fill('\u0000')
        pending = null
    }

    override suspend fun forgotPassword(mobile: String): ActionResult =
        safeApiCall(json, allowEmptyData = true, authenticated = false) { api.forgotPassword(ForgotPasswordRequest(mobile)) }.toAction()

    override suspend fun resetPassword(mobile: String, otp: String, newPassword: String): ActionResult =
        safeApiCall(json, allowEmptyData = true, authenticated = false) {
            api.resetPassword(ResetPasswordRequest(mobile, otp, newPassword, newPassword))
        }.toAction()

    override suspend fun logout() {
        val refresh = tokenStore.read()?.refreshToken
        runCatching { safeApiCall(json, allowEmptyData = true) { api.logout(LogoutRequest(refresh)) } }
    }

    private fun livePending(): Pending? {
        val p = pending ?: return null
        if (now() - p.createdAt > PENDING_TTL_MS) { clearPending(); return null }
        return p
    }

    private suspend fun completeSignIn(data: LoginData): LoginOutcome {
        val access = data.accessToken ?: return LoginOutcome.Failed(AuthError.Unknown)
        val refresh = data.refreshToken ?: return LoginOutcome.Failed(AuthError.Unknown)
        tokenStore.save(SessionTokens(access, refresh))
        val user = data.data.toUser(data.userType)
        local.saveUser(user)
        return LoginOutcome.SignedIn(user)
    }

    companion object {
        const val PENDING_TTL_MS = 5 * 60_000L
    }
}

internal fun UserDto?.toUser(userType: String?): User {
    val first = this?.fname.orEmpty().trim()
    val last = this?.lname.orEmpty().trim()
    val name = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "Retailer" }
    return User(
        id = this?.id ?: 0,
        regno = this?.regno ?: 0,
        name = name,
        shopName = this?.companyName?.trim().orEmpty().ifBlank { name },
        phone = this?.phone.orEmpty(),
        email = this?.email.orEmpty(),
        userType = userType ?: "Retailer",
        kycDone = this?.kycStatus == 1,
    )
}

/** Server text is shown only if it looks like a user-facing message (never internal errors such as Prisma traces). */
internal fun safeServerMessage(message: String): String? {
    val m = message.trim()
    val internal = listOf("prisma", "invocation", "column", "database", "sql", "exception", "stack", "undefined", "validation error:")
    if (m.isEmpty() || m.length > 160 || internal.any { m.contains(it, ignoreCase = true) }) return null
    return m
}

internal fun NetworkResult.Failure.toAuthError(): AuthError = when (this) {
    is NetworkResult.Failure.Api -> safeServerMessage(message)?.let { AuthError.Server(it) } ?: AuthError.Unknown
    NetworkResult.Failure.NoInternet -> AuthError.NoInternet
    NetworkResult.Failure.Timeout -> AuthError.Timeout
    NetworkResult.Failure.InsecureConnection -> AuthError.Insecure
    NetworkResult.Failure.Unauthorized, is NetworkResult.Failure.Unexpected -> AuthError.Unknown
}

private fun NetworkResult.Failure.toLoginFailure(): LoginOutcome {
    if (this is NetworkResult.Failure.Api) {
        val msg = safeServerMessage(message)
        val locked = msg != null && (msg.contains("locked", ignoreCase = true) || msg.contains("too many", ignoreCase = true)) &&
            !msg.contains("remaining", ignoreCase = true)
        if (locked) return LoginOutcome.Locked(msg!!)
    }
    return LoginOutcome.Failed(toAuthError())
}

private fun NetworkResult<*>.toAction(): ActionResult = when (this) {
    is NetworkResult.Success -> ActionResult.Ok
    is NetworkResult.Failure -> ActionResult.Failed(toAuthError())
}
