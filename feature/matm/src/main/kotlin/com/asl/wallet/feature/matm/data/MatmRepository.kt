package com.asl.wallet.feature.matm.data

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.DEFAULT_ERROR
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.feature.matm.domain.MatmConfirm
import com.asl.wallet.feature.matm.domain.MatmInit
import com.asl.wallet.feature.matm.domain.MatmResult
import com.asl.wallet.feature.matm.domain.MatmTxnType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.Response
import java.io.IOException
import java.math.BigDecimal
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

interface MatmRepository {
    /**
     * Starts a transaction on the server and returns the credentials the card reader needs.
     * [key] is generated once per attempt by the caller and reused on retries so the backend
     * de-duplicates instead of starting a second transaction.
     */
    suspend fun initiate(type: MatmTxnType, customerMobile: String, amount: BigDecimal, key: IdempotencyKey): NetworkResult<MatmInit>

    /** Posts the card-reader result back to the server. */
    suspend fun updateStatus(result: MatmResult): NetworkResult<MatmConfirm>
}

@Singleton
class MatmRepositoryImpl @Inject constructor(
    private val api: MatmApi,
    private val json: Json,
) : MatmRepository {

    override suspend fun initiate(type: MatmTxnType, customerMobile: String, amount: BigDecimal, key: IdempotencyKey): NetworkResult<MatmInit> {
        val amt = if (type == MatmTxnType.Withdraw) amount.stripTrailingZeros().toPlainString() else "0"
        val body = InitiateRequest(amount = amt, customer_mobile = customerMobile, txn_type = type.code)
        return matmCall { api.initiate(body, key.value) }.map { it.credentials.toInit(type) }
    }

    override suspend fun updateStatus(result: MatmResult): NetworkResult<MatmConfirm> {
        val body = UpdateStatusRequest(
            balanceAmount = result.balanceAmount, bankName = result.bankName, bankRrn = result.bankRrn,
            cardNum = result.cardNum, cardType = result.cardType, errorCode = result.errorCode, fpId = result.fpId,
            message = result.message, mobileNumber = result.mobileNumber, status = result.status,
            terminalId = result.terminalId, transAmount = result.transAmount, transType = result.transType,
            txnId = result.txnId, type = result.type.toString(),
        )
        return matmCall { api.updateStatus(body) }.map { it.toConfirm() }
    }

    /**
     * Like core `safeApiCall`, but for the matm responses whose fields come back FLAT (status/message
     * next to the payload) rather than wrapped in the standard data envelope. Success needs HTTP 2xx
     * and `status == true`; the payload is read off the body itself, so an initiate that returns the
     * credentials at the top level no longer reports a false "Empty response".
     */
    private suspend fun <T : MatmBody> matmCall(call: suspend () -> Response<T>): NetworkResult<T> {
        return try {
            val res = call()
            val body = res.body()
            when {
                res.code() == 401 -> NetworkResult.Failure.Unauthorized
                res.isSuccessful && body != null && body.status -> NetworkResult.Success(body, body.message)
                res.isSuccessful && body != null -> NetworkResult.Failure.Api(body.statusCode ?: res.code(), null, body.message ?: DEFAULT_ERROR)
                else -> NetworkResult.Failure.Api(res.code(), null, errorMessage(res) ?: DEFAULT_ERROR)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SSLException) {
            NetworkResult.Failure.InsecureConnection
        } catch (e: SocketTimeoutException) {
            NetworkResult.Failure.Timeout
        } catch (e: UnknownHostException) {
            NetworkResult.Failure.NoInternet
        } catch (e: ConnectException) {
            NetworkResult.Failure.NoInternet
        } catch (e: IOException) {
            NetworkResult.Failure.NoInternet
        } catch (e: SerializationException) {
            NetworkResult.Failure.Unexpected("Unreadable response")
        }
    }

    /** Best-effort message from a non-2xx error body. */
    private fun errorMessage(res: Response<*>): String? {
        val raw = runCatching { res.errorBody()?.string() }.getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val errorObj = obj["error"] as? JsonObject
        fun text(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.ifEmpty { null }
        return text(errorObj, "message") ?: text(obj, "message")
    }

    private fun MatmInitDto.toInit(requested: MatmTxnType): MatmInit = MatmInit(
        superMerchantId = superMerchantId.orEmpty(),
        merchantUserId = merchantUserId.orEmpty(),
        merchantPassword = merchantPassword.orEmpty(),
        mobileNumber = mobileNumber.orEmpty(),
        amount = amount?.contentOrNull ?: "0",
        remarks = remarks.orEmpty(),
        txnId = txnId.orEmpty(),
        imei = imei.orEmpty(),
        amountEditable = amountEditable ?: false,
        type = type?.let(MatmTxnType::ofInitType) ?: requested,
    )

    private fun MatmStatusResponse.toConfirm(): MatmConfirm {
        val r = result
        val failed = r.tranStatus?.equals("FAILED", ignoreCase = true) == true || serverError != null
        return MatmConfirm(
            ok = !failed,
            message = serverError ?: r.message ?: message,
            commission = r.tranCommAmt?.contentOrNull?.let { runCatching { BigDecimal(it) }.getOrNull() }?.takeIf { it.signum() > 0 },
            finalStatus = r.tranStatus,
        )
    }
}
