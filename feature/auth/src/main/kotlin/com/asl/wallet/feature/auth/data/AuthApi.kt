package com.asl.wallet.feature.auth.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

/** Merchant auth endpoints (API sheet rows 4–13, verified on UAT 3 Nov 2026). */
interface AuthApi {

    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @POST("merchant/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiEnvelope<LoginData>>

    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @POST("merchant/auth/verify-otp")
    suspend fun verifyOtp(@Body body: VerifyOtpRequest): Response<ApiEnvelope<LoginData>>

    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @POST("merchant/auth/resend-otp")
    suspend fun resendOtp(@Body body: ResendOtpRequest): Response<ApiEnvelope<JsonElement>>

    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @POST("merchant/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ApiEnvelope<JsonElement>>

    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @POST("merchant/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/auth/logout")
    suspend fun logout(@Body body: LogoutRequest): Response<ApiEnvelope<JsonElement>>

    @GET("merchant/auth/me")
    suspend fun me(): Response<ApiEnvelope<UserDto>>
}

/** Exactly one of [phone] / [email] is set; nulls are not sent (explicitNulls = false). */
@Serializable
data class LoginRequest(val phone: String? = null, val email: String? = null, val password: String)

@Serializable
data class VerifyOtpRequest(@SerialName("user_id") val userId: String, val password: String, val otp: String)

@Serializable
data class ResendOtpRequest(@SerialName("user_id") val userId: String, val password: String)

@Serializable
data class ForgotPasswordRequest(val phone: String)

@Serializable
data class ResetPasswordRequest(
    val phone: String,
    val otp: String,
    @SerialName("new_password") val newPassword: String,
    @SerialName("confirm_password") val confirmPassword: String,
)

@Serializable
data class LogoutRequest(val refreshToken: String? = null)

@Serializable
data class LoginData(
    val requiresOtp: Boolean = false,
    val userType: String? = null,
    val data: UserDto? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

/**
 * Only the fields the app needs. Everything else the server sends (password hash, full Aadhaar/PAN,
 * api tokens…) is ignored by the parser and never kept. See API_REVIEW_FOR_BACKEND.md finding 3.
 */
@Serializable
data class UserDto(
    val id: Long? = null,
    val regno: Long? = null,
    val fname: String? = null,
    val lname: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("company_name") val companyName: String? = null,
    @SerialName("kyc_status") val kycStatus: Int? = null,
    val proimg: String? = null,
)
