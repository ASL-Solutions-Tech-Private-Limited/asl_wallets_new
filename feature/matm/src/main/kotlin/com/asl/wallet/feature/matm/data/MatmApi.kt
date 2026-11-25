package com.asl.wallet.feature.matm.data

import com.asl.wallet.core.network.interceptor.ApiHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Micro-ATM endpoints (new backend, merchant/matm/*). Amounts come back as "10"/10/10.0, so they are
// read as JsonPrimitive. The backend returns the SDK credentials / status FLAT at the top level
// (same shape as the old app's InitiateMATMResponse), not wrapped in the standard data envelope, so
// these responses are NOT ApiEnvelope<T>: the status/message/error fields sit next to the payload.
// A nested "data" object is still accepted as a fallback in case the backend ever wraps it.
interface MatmApi {
    @POST("merchant/matm/initiate")
    suspend fun initiate(
        @Body body: InitiateRequest,
        @Header(ApiHeaders.IDEMPOTENCY_KEY) idempotencyKey: String,
    ): Response<MatmInitResponse>

    @POST("merchant/matm/update-status")
    suspend fun updateStatus(@Body body: UpdateStatusRequest): Response<MatmStatusResponse>
}

@Serializable
data class InitiateRequest(
    val amount: String,
    val customer_mobile: String,
    val txn_type: String,
)

// Common envelope fields the backend returns flat on every matm response.
sealed interface MatmBody {
    val status: Boolean
    val statusCode: Int?
    val message: String?
}

// The initiate response. Credentials are read from the flat top-level fields, or from a nested
// "data" object if the backend wraps them. type: 2 = Cash Withdrawal, 4 = Balance Enquiry.
@Serializable
data class MatmInitResponse(
    override val status: Boolean = false,
    override val statusCode: Int? = null,
    override val message: String? = null,
    val error: String? = null,
    val data: MatmInitDto? = null,
    val superMerchantId: String? = null,
    val merchantUserId: String? = null,
    val merchantPassword: String? = null,
    val mobileNumber: String? = null,
    val amount: JsonPrimitive? = null,
    val remarks: String? = null,
    val txnId: String? = null,
    val imei: String? = null,
    val amountEditable: Boolean? = null,
    val type: Int? = null,
) : MatmBody {
    val credentials: MatmInitDto
        get() = data ?: MatmInitDto(
            superMerchantId = superMerchantId,
            merchantUserId = merchantUserId,
            merchantPassword = merchantPassword,
            mobileNumber = mobileNumber,
            amount = amount,
            remarks = remarks,
            txnId = txnId,
            imei = imei,
            amountEditable = amountEditable,
            type = type,
            message = message,
        )
}

// The SDK credentials + parameters, whether they arrive flat or under "data".
@Serializable
data class MatmInitDto(
    val superMerchantId: String? = null,
    val merchantUserId: String? = null,
    val merchantPassword: String? = null,
    val mobileNumber: String? = null,
    val amount: JsonPrimitive? = null,
    val remarks: String? = null,
    val txnId: String? = null,
    val imei: String? = null,
    val amountEditable: Boolean? = null,
    val type: Int? = null,
    val message: String? = null,
)

// Posted verbatim after the card read - field names match the FingPay SDK result / Postman body.
@Serializable
data class UpdateStatusRequest(
    val balanceAmount: Double,
    val bankName: String,
    val bankRrn: String,
    val cardNum: String,
    val cardType: String,
    val errorCode: String,
    val fpId: String,
    val message: String,
    val mobileNumber: String,
    val status: Boolean,
    val terminalId: String,
    val transAmount: Double,
    val transType: String,
    val txnId: String,
    val type: String,
)

// The server confirmation of an update-status post. Lenient: the receipt is built from the device
// result; this adds the commission / final status when the server returns them. Like initiate, the
// fields may arrive flat or under "data"; a non-empty "error" means the server could not confirm.
@Serializable
data class MatmStatusResponse(
    override val status: Boolean = false,
    override val statusCode: Int? = null,
    override val message: String? = null,
    val error: String? = null,
    val data: MatmStatusDto? = null,
    val tranStatus: String? = null,
    val tranCommAmt: JsonPrimitive? = null,
) : MatmBody {
    val result: MatmStatusDto
        get() = data ?: MatmStatusDto(tranStatus = tranStatus, tranCommAmt = tranCommAmt, message = message)
    val serverError: String? get() = error?.takeIf { it.isNotBlank() }
}

@Serializable
data class MatmStatusDto(
    val tranStatus: String? = null,
    val tranCommAmt: JsonPrimitive? = null,
    val message: String? = null,
)
