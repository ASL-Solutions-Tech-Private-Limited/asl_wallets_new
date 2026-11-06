package com.asl.wallet.feature.wallet.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Wallet endpoints (merchant/my-wallet/...). Shapes checked read-only on UAT on 4 Nov 2026 and against the backend
 * source. Every call that moves money carries the Idempotency-Key header.
 */
interface WalletApi {
    @GET("merchant/my-wallet/account-info")
    suspend fun accountInfo(): Response<ApiEnvelope<AccountInfoDto>>

    @GET("merchant/ledger-report")
    suspend fun ledger(@Query("page") page: Int = 1, @Query("limit") limit: Int = 3): Response<ApiEnvelope<List<LedgerDto>>>

    // ---- Add money (PayU)
    @GET("merchant/my-wallet/add-money")
    suspend fun addMoneyInfo(): Response<ApiEnvelope<AddMoneyInfoDto>>

    @POST("merchant/my-wallet/add-money")
    suspend fun addMoney(
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
        @Body body: AddMoneyRequest,
    ): Response<ApiEnvelope<JsonObject>>

    @GET("merchant/my-wallet/add-money/status/{txnId}")
    suspend fun addMoneyStatus(@Path("txnId") txnId: String): Response<ApiEnvelope<AddMoneyStatusDto>>

    /**
     * ⚠ Proposed to the backend (not live yet): PayU asks for extra hashes while its screens are open. The server
     * must sign only strings for this retailer's own [PayUHashRequest.txnid], so it can't be used to sign anything else.
     */
    @POST("merchant/my-wallet/add-money/hash")
    suspend fun payuHash(@Body body: PayUHashRequest): Response<ApiEnvelope<PayUHashDto>>

    // ---- Fund request (always to Admin)
    @GET("merchant/my-wallet/fund-request")
    suspend fun fundRequestInfo(): Response<ApiEnvelope<FundRequestInfoDto>>

    @GET("merchant/my-wallet/fund-request/banks")
    suspend fun fundRequestBanks(@Query("reqform") reqform: String): Response<ApiEnvelope<List<CompanyBankDto>>>

    @POST("merchant/my-wallet/fund-request")
    suspend fun createFundRequest(@Body body: FundRequestBody): Response<ApiEnvelope<JsonElement>>

    @PUT("merchant/my-wallet/fund-request/{id}")
    suspend fun updateFundRequest(@Path("id") id: Long, @Body body: FundRequestBody): Response<ApiEnvelope<JsonElement>>

    @DELETE("merchant/my-wallet/fund-request/{id}")
    suspend fun deleteFundRequest(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>

    @GET("merchant/fund-requests")
    suspend fun fundRequests(
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("status") status: String?,
        @Query("sort") sort: String = "desc",
    ): Response<ApiEnvelope<List<FundRequestRowDto>>>

    // ---- Send to own bank
    @GET("merchant/my-wallet/send-money")
    suspend fun sendMoneyInfo(): Response<ApiEnvelope<SendMoneyInfoDto>>

    /** First call without [SendMoneyRequest.otp] sends the OTP; the second call with it moves the money. */
    @POST("merchant/my-wallet/send-money")
    suspend fun sendMoney(
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String?,
        @Body body: SendMoneyRequest,
    ): Response<ApiEnvelope<SendMoneyResultDto>>

    // ---- Bank accounts
    @GET("merchant/my-wallet/bank-account")
    suspend fun bankAccounts(): Response<ApiEnvelope<List<BankAccountDto>>>

    @GET("merchant/my-wallet/bank-account/options")
    suspend fun bankOptions(): Response<ApiEnvelope<List<BankOptionDto>>>

    @Multipart
    @POST("merchant/my-wallet/bank-account")
    suspend fun addBankAccount(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part panimg: MultipartBody.Part,
        @Part passbookimg: MultipartBody.Part,
    ): Response<ApiEnvelope<BankAccountDto>>

    @DELETE("merchant/my-wallet/bank-account/{id}")
    suspend fun deleteBankAccount(@Path("id") id: Long): Response<ApiEnvelope<JsonElement>>
}

/** The server sends snake_case and camelCase, and amounts as "23.00" or 23. Both are read. */
@Serializable
data class AccountInfoDto(
    val available_balance_amt: JsonPrimitive? = null,
    val availableBalanceAmt: JsonPrimitive? = null,
    val e_topup_purchase: JsonPrimitive? = null,
    val eTopupPurchase: JsonPrimitive? = null,
    val sales_amt: JsonPrimitive? = null,
    val salesAmt: JsonPrimitive? = null,
    val commission_received: JsonPrimitive? = null,
    val commissionReceived: JsonPrimitive? = null,
)

@Serializable
data class LedgerDto(
    val id: Long? = null,
    val transdesc: String? = null,
    val transDate: String? = null,
    val transTime: String? = null,
    val creditAmt: JsonPrimitive? = null,
    val debitAmt: JsonPrimitive? = null,
    val service: String? = null,
)

@Serializable data class LimitsDto(val minAmount: JsonPrimitive? = null, val maxAmount: JsonPrimitive? = null)
@Serializable data class WalletInfoDto(val balanceAmt: JsonPrimitive? = null, val balance_amt: JsonPrimitive? = null, val lockAmt: JsonPrimitive? = null)

@Serializable
data class AddMoneyInfoDto(
    val wallet: WalletInfoDto? = null,
    val limits: LimitsDto? = null,
    val paymentModes: List<String> = emptyList(),
)

@Serializable data class AddMoneyRequest(val amount: String)
@Serializable data class PayUHashRequest(val txnid: String, val hashName: String, val hashString: String)
@Serializable data class PayUHashDto(val hash: String? = null)

@Serializable
data class AddMoneyStatusDto(
    val clientTxnId: String? = null,
    val txnid: String? = null,
    val orderId: String? = null,
    val amount: JsonPrimitive? = null,
    val status: String? = null,
    val isPaymentComplete: Boolean? = null,
    val mode: String? = null,
    val paymentMode: String? = null,
    val mihpayid: String? = null,
    val bankRefNo: String? = null,
    val reason: String? = null,
    val message: String? = null,
)

@Serializable data class RequestFromDto(val id: JsonPrimitive? = null, val name: String? = null, val role: String? = null)

@Serializable
data class CompanyBankDto(
    val id: JsonPrimitive? = null,
    val bankName: String? = null,
    val branchName: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val displayName: String? = null,
)

@Serializable
data class FundRequestInfoDto(
    val requestFromOptions: List<RequestFromDto> = emptyList(),
    val banks: List<CompanyBankDto> = emptyList(),
    val paymentModes: List<String> = emptyList(),
    /** The retailer's latest requests (same item shape as the report, bank inside `bankDetails`). */
    val recentRequests: List<FundRequestRowDto> = emptyList(),
)

@Serializable
data class FundRequestBody(
    val amount: String,
    val reqform: String,
    val bankid: Long,
    val tranid: String,
    val pwerby: String,
    /** YYYY-MM-DD */
    val dipodate: String,
    val remarks: String? = null,
)

/** `fund-requests` returns the rows as `data` and `meta.summary` beside them (UAT, 4 Nov 2026). */
@Serializable data class FundSummaryDto(val totalPendingRequests: JsonPrimitive? = null, val totalPendingAmount: JsonPrimitive? = null)

@Serializable
data class FundRequestRowDto(
    val id: Long? = null,
    val tranNo: String? = null,
    val tranId: String? = null,
    val requestAmount: JsonPrimitive? = null,
    val amount: JsonPrimitive? = null,
    val walletType: String? = null,
    val paymentMode: String? = null,
    val pwerby: String? = null,
    val bankName: String? = null,
    val accountNo: String? = null,
    val bankId: JsonPrimitive? = null,
    val remarks: String? = null,
    val adminRemarks: String? = null,
    val depositDate: String? = null,
    val tranDate: String? = null,
    val tranTime: String? = null,
    val status: String? = null,
    val statusLabel: String? = null,
    val transferStatus: JsonPrimitive? = null,
    val declineStatus: JsonPrimitive? = null,
    val reqform: String? = null,
    /** `my-wallet/fund-request` nests the bank here instead of bankName/accountNo. */
    val bankDetails: CompanyBankDto? = null,
)

@Serializable
data class SendAccountDto(
    val id: JsonPrimitive? = null,
    val bankName: String? = null,
    val accno: String? = null,
    val ifsccode: String? = null,
    val holdername: String? = null,
    val isverify: JsonPrimitive? = null,
)

@Serializable
data class SendMoneyInfoDto(
    val accounts: List<SendAccountDto> = emptyList(),
    val wallet: WalletInfoDto? = null,
    val limits: LimitsDto? = null,
    val payoutModes: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val user: SendUserDto? = null,
)

@Serializable data class SendUserDto(val phone: String? = null)

@Serializable data class SendMoneyRequest(val payto: String, val payamt: String, val paytype: String, val otp: String? = null)

@Serializable
data class SendMoneyResultDto(
    val step: String? = null,
    val servid: String? = null,
    val refno: String? = null,
    val utr: String? = null,
    val payamt: JsonPrimitive? = null,
    val serviceCharge: JsonPrimitive? = null,
    val totalDebited: JsonPrimitive? = null,
    val beneficiaryName: String? = null,
    val accountNumber: String? = null,
    val bankName: String? = null,
    val paytype: String? = null,
    /** "SUCCESS" / "PENDING" / "FAILED", or a boolean when the server reports an inner failure (seen on DMT, 5 Nov). */
    val status: JsonPrimitive? = null,
    val message: String? = null,
)

@Serializable
data class BankAccountDto(
    val id: Long? = null,
    val bankName: String? = null,
    val bank_name: String? = null,
    val accountNo: String? = null,
    val accno: String? = null,
    val ifscCode: String? = null,
    val ifsccode: String? = null,
    val holderName: String? = null,
    val holdername: String? = null,
    val accountType: String? = null,
    val acctype: String? = null,
    val isVerified: Boolean? = null,
    val is_verified: Boolean? = null,
)

@Serializable
data class BankOptionDto(
    val id: JsonPrimitive? = null,
    val bankName: String? = null,
    val bank_name: String? = null,
    val bankCode: String? = null,
)
