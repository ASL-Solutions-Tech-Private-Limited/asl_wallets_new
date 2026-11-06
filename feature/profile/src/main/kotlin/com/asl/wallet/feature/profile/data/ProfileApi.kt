package com.asl.wallet.feature.profile.data

import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Profile & settings endpoints (shapes checked on UAT and in asl-backend, 4 Nov 2026). Profile is read-only for now. */
interface ProfileApi {
    @GET("merchant/auth/me")
    suspend fun me(): Response<ApiEnvelope<ProfileDto>>

    @GET("merchant/commission")
    suspend fun commission(): Response<ApiEnvelope<CommissionDto>>

    @GET("merchant/services")
    suspend fun services(): Response<ApiEnvelope<List<ServiceStatusDto>>>

    @POST("merchant/auth/change-password/request-otp")
    suspend fun requestChangePasswordOtp(@Body body: ChangePasswordRequest): Response<ApiEnvelope<ChangePasswordOtpDto>>

    @POST("merchant/auth/change-password/verify-otp")
    suspend fun verifyChangePassword(@Body body: VerifyChangePasswordRequest): Response<ApiEnvelope<JsonElement>>
}

/**
 * Only the fields the Profile screens show. The server sends much more (including the full Aadhaar and
 * password hash: reported to backend); unknown keys are ignored and never kept.
 */
@Serializable
data class ProfileDto(
    val regno: Long? = null,
    val fname: String? = null,
    val lname: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val company_name: String? = null,
    val panno: String? = null,
    val addsdt: String? = null,
    val landmark: String? = null,
    val city: String? = null,
    val pincode: String? = null,
)

@Serializable
data class CommissionDto(
    val prepaidServices: List<PrepaidGroupDto> = emptyList(),
    val bankingServices: List<BankingGroupDto> = emptyList(),
)

@Serializable
data class PrepaidGroupDto(val typeId: Int? = null, val typeName: String? = null, val operators: List<OperatorDto> = emptyList())

@Serializable
data class OperatorDto(val operatorName: String? = null, val displayAmount: String? = null, val isSurcharge: Int? = null, val status: Int? = null)

@Serializable
data class BankingGroupDto(val interfaceId: Int? = null, val title: String? = null, val serviceType: String? = null, val slabs: List<SlabDto> = emptyList())

@Serializable
data class SlabDto(val rangeText: String? = null, val displayAmount: String? = null, val status: Int? = null)

@Serializable
data class ServiceStatusDto(val id: Long? = null, val services_name: String? = null, val service_desc: String? = null, val status: JsonPrimitive? = null)

@Serializable
data class ChangePasswordRequest(val old_password: String, val new_password: String, val confirm_password: String)

@Serializable
data class ChangePasswordOtpDto(val sentTo: String? = null, val channel: String? = null, val expiresIn: String? = null)

@Serializable
data class VerifyChangePasswordRequest(val otp: String)
