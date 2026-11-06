package com.asl.wallet.feature.aeps.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

/** AEPS endpoints of the new backend (`src/modules/merchant/aeps`, checked 4 Nov 2026). */
interface AepsApi {
    @GET("merchant/aeps/init")
    suspend fun init(): Response<ApiEnvelope<InitDto>>

    @POST("merchant/aeps/checkAgentStatus")
    suspend fun checkAgentStatus(@Body body: StatusRequest): Response<ApiEnvelope<StatusDto>>

    @Multipart
    @POST("merchant/aeps/onboarding")
    suspend fun onboarding(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part aadharImage: MultipartBody.Part,
        @Part panImage: MultipartBody.Part,
        @Part shopImage: MultipartBody.Part,
    ): Response<ApiEnvelope<OtpRefDto>>

    @POST("merchant/aeps/sendotp")
    suspend fun sendOtp(@Body body: SendOtpRequest): Response<ApiEnvelope<OtpRefDto>>

    @POST("merchant/aeps/validateOtp")
    suspend fun validateOtp(@Body body: ValidateOtpRequest): Response<ApiEnvelope<ValidatedOtpDto>>

    @POST("merchant/aeps/biometrickyc")
    suspend fun biometricKyc(@Body body: BiometricRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/aeps/sendotp-bank")
    suspend fun bankSendOtp(@Body body: SendOtpRequest): Response<ApiEnvelope<OtpRefDto>>

    @POST("merchant/aeps/bank-validateOtp")
    suspend fun bankValidateOtp(@Body body: ValidateOtpRequest): Response<ApiEnvelope<ValidatedOtpDto>>

    @POST("merchant/aeps/bank-biometrickyc")
    suspend fun bankBiometricKyc(@Body body: BiometricRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/aeps/two-factor-authenticate")
    suspend fun dailyLogin(@Body body: DailyLoginRequest): Response<ApiEnvelope<JsonElement>>

    @POST("merchant/aeps/generate-otp")
    suspend fun transactionOtp(@Body body: TxnOtpRequest): Response<ApiEnvelope<TxnOtpDto>>

    @POST("merchant/aeps/transaction")
    suspend fun transaction(
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @Body body: TxnRequest,
    ): Response<ApiEnvelope<TxnDto>>
}

/** The retailer's own identity fields from `auth/me`, needed by AEPS requests. Held in memory only. */
interface AgentIdentityApi {
    @GET("merchant/auth/me")
    suspend fun me(): Response<ApiEnvelope<AgentMeDto>>
}

@Serializable
data class AgentMeDto(
    val aadharno: String? = null, val phone: String? = null, val email: String? = null, val panno: String? = null,
    val fname: String? = null, val lname: String? = null, val addsdt: String? = null, val city: String? = null,
    val pincode: String? = null, val company_name: String? = null,
)

@Serializable
data class InitDto(
    val deviceInfo: List<DeviceDto> = emptyList(),
    val providerInfo: List<ProviderDto> = emptyList(),
    val bankList: List<BankDto> = emptyList(),
    val allowedAmount: List<JsonPrimitive> = emptyList(),
)
@Serializable data class DeviceDto(val name: String? = null, val deviceId: String? = null, val isActive: JsonPrimitive? = null)
@Serializable data class ProviderDto(val name: String? = null, val providerId: JsonPrimitive? = null, val isActive: JsonPrimitive? = null)
@Serializable data class BankDto(val bankName: String? = null, val bankIino: String? = null)

@Serializable data class StatusRequest(val providerId: Int, val aadhaarNo: String, val mobileNo: String)

@Serializable
data class StatusDto(
    val isEkycDone: Boolean = false,
    val isBankEkycDone: Boolean = false,
    val isBioKyc: Boolean = false,
    val isTwoFA: Boolean = false,
    val providerId: Int? = null,
    val outletid: String? = null,
    val pidOptionWadh: String? = null,
    val referenceKey: String? = null,
    /** `{}` when empty, an object otherwise: kept as JSON and read leniently. */
    val bcAgentData: JsonElement? = null,
)

@Serializable data class OtpRefDto(val otpReferenceId: JsonPrimitive? = null, val hash: String? = null, val merchantLoginId: String? = null)

@Serializable
data class SendOtpRequest(
    val phone: String, val aadharNo: String, val panNo: String, val latitude: String, val longitude: String,
    val merchantLoginId: String, val providerId: Int, val email: String? = null,
)

@Serializable data class ValidateOtpRequest(val otp: String, val otpReferenceID: String, val hash: String, val providerId: Int)

@Serializable
data class ValidatedOtpDto(
    val outletId: String? = null,
    val primaryKeyId: JsonPrimitive? = null,
    val encodeFPTxnId: String? = null,
    val pidOptionWadh: String? = null,
    val referenceKey: String? = null,
)

@Serializable
data class BiometricRequest(
    val aadhaarNo: String, val merchantLoginId: String? = null, val otpReferenceId: String? = null, val hash: String,
    val providerId: Int, val captureType: String, val biometricData: String, val latitude: String, val longitude: String,
)

@Serializable
data class DailyLoginRequest(
    val aadhaarNo: String, val providerId: Int, val captureType: String, val biometricData: String,
    val latitude: String, val longitude: String, val outletId: String, val mobile: String,
)

@Serializable
data class TxnOtpRequest(
    val aadhaarNo: String, val txnType: String, val latitude: String, val longitude: String,
    val amount: String, val bankiin: String, val mobile: String, val providerId: Int,
)

/** The backend currently drops `fpTransactionId` from this response (reported); both places are read. */
@Serializable data class TxnOtpDto(val fpTransactionId: String? = null, val otpTransactionId: String? = null)

@Serializable
data class TxnRequest(
    val aadhaarNo: String, val txnType: String, val latitude: String, val longitude: String, val captureType: String,
    val outletId: String, val amount: String, val bankiin: String, val mobile: String, val providerId: Int,
    val otp_transation_id: String? = null, val biometricData: String,
)

@Serializable
data class TxnDto(
    val aepsmode: String? = null,
    val timestamp: String? = null,
    val txnReferenceId: String? = null,
    val bankAccountBalance: String? = null,
    val bank: String? = null,
    /** The server echoes the full customer Aadhaar here (reported). It is never read. */
    val transactionAmount: String? = null,
    val miniStatementData: List<JsonElement> = emptyList(),
)
