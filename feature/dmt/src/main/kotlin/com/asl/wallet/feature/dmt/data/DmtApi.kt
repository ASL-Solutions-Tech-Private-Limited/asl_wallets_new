package com.asl.wallet.feature.dmt.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DMT endpoints (merchant/dmt/...). The collection has no example responses and the local backend has no DMT module,
 * so field names follow the old app (same calls on the older API) and are read leniently.
 * The app never sends `apiToken` (API partners only, user 4 Nov 2026).
 */
interface DmtApi {
    @POST("merchant/dmt/check-remitter")
    suspend fun checkRemitter(@Body body: RemitterLookup): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/add-remitter")
    suspend fun addRemitter(@Body body: AddRemitterRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/generate-otp")
    suspend fun generateOtp(@Body body: OtpRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/verify-remitter")
    suspend fun verifyOtp(@Body body: OtpRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/beneficiary-list")
    suspend fun recipients(@Body body: PhoneRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/bank-list")
    suspend fun banks(@Body body: JsonObject = JsonObject(emptyMap())): Response<ApiEnvelope<List<BankDto>>>

    @POST("merchant/dmt/validate-beneficiary-name")
    suspend fun checkName(@Body body: NameCheckRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/add-beneficiary")
    suspend fun addRecipient(@Body body: AddRecipientRequest): Response<ApiEnvelope<JsonElement>>

    /** ⚠ Proposed (404 on UAT, 5 Nov): charge for an amount and mode, shown before the OTP. */
    @POST("merchant/dmt/charges")
    suspend fun charges(@Body body: ChargesRequest): Response<ApiEnvelope<JsonElement>>

    /** OTP to the retailer's own registered mobile, to approve the transfer. No body. */
    @POST("merchant/dmt/dmt-send-otp")
    suspend fun sendTransferOtp(@Body body: JsonObject = JsonObject(emptyMap())): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/dmt/money-transfer")
    suspend fun transfer(
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @Body body: TransferRequest,
    ): Response<ApiEnvelope<JsonElement>>

    @GET("merchant/my-wallet/account-info")
    suspend fun accountInfo(): Response<ApiEnvelope<BalanceDto>>
}

@Serializable data class RemitterLookup(val phone: String, val otpReference: String)
@Serializable data class AddRemitterRequest(val name: String, val phone: String, val gender: String)
@Serializable data class OtpRequest(val phone: String, val otpReference: String, val otp: String? = null, val beneficiaryAccno: String? = null)
@Serializable data class PhoneRequest(val phone: String)
@Serializable data class BankDto(val id: JsonPrimitive? = null, val bankName: String? = null, val bankCode: String? = null, val status: JsonPrimitive? = null)
@Serializable data class NameCheckRequest(val accountNumber: String, val ifscCode: String, val beneMobile: String, val bankName: String, val isExist: Int)

@Serializable
data class AddRecipientRequest(
    val phone: String,
    val beneficiaryName: String,
    val beneficiaryAccNo: String,
    val bankIfscCod: String,
    val beneficiaryBankName: String,
    val beneficiaryMobileNumber: String,
    /** Always sent (no default: the JSON encoder skips default values). */
    val trnasferMode: String,
    val isVerified: String,
)

@Serializable data class ChargesRequest(val amount: Int, val transferType: String)
@Serializable data class TransferRequest(val phone: String, val beneficiaryId: String, val transferType: String, val amount: String, val otp: String)
@Serializable data class BalanceDto(val available_balance_amt: JsonPrimitive? = null, val availableBalanceAmt: JsonPrimitive? = null)
