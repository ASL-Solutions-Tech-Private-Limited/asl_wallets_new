package com.asl.wallet.core.network

import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/** Result of every API call. ViewModels only ever see this type, never Retrofit or OkHttp exceptions. */
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T, val message: String?) : NetworkResult<T>

    sealed interface Failure : NetworkResult<Nothing> {
        /** The server answered with a business or validation error. [message] is safe to show to the user. */
        data class Api(val httpCode: Int, val code: String?, val message: String) : Failure
        /** Session is no longer valid. The app signs out and shows Login. */
        data object Unauthorized : Failure
        data object NoInternet : Failure
        /**
         * The request may or may not have reached the server. For money actions the UI must check the
         * transaction status and must NOT blindly retry with a new idempotency key.
         */
        data object Timeout : Failure
        /** TLS or certificate pin failure: possible interception. Never retried, and reported. */
        data object InsecureConnection : Failure
        data class Unexpected(val reason: String) : Failure
    }
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(data), message)
    is NetworkResult.Failure -> this
}

/**
 * Runs a Retrofit call that returns the standard envelope and turns every outcome into [NetworkResult].
 * Success needs HTTP 2xx, `status == true` and a non-null `data` (unless [allowEmptyData]).
 */
suspend fun <T> safeApiCall(
    json: Json,
    allowEmptyData: Boolean = false,
    /**
     * false for sign-in endpoints (login, OTP, forgot/reset password): there a 401 means
     * "wrong credentials" and carries a message for the user, not "session expired".
     */
    authenticated: Boolean = true,
    call: suspend () -> Response<ApiEnvelope<T>>,
): NetworkResult<T> {
    return try {
        val response = call()
        val body = response.body()
        when {
            response.code() == 401 && authenticated -> NetworkResult.Failure.Unauthorized
            response.isSuccessful && body != null && body.status -> {
                val data = body.data
                @Suppress("UNCHECKED_CAST")
                when {
                    data != null -> NetworkResult.Success(data, body.message)
                    allowEmptyData -> NetworkResult.Success(Unit as T, body.message)
                    else -> NetworkResult.Failure.Unexpected("Empty response")
                }
            }
            response.isSuccessful && body != null -> NetworkResult.Failure.Api(
                httpCode = body.statusCode ?: response.code(),
                code = body.error?.code,
                message = body.error?.message ?: body.message ?: DEFAULT_ERROR,
            )
            else -> parseError(json, response)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SSLPeerUnverifiedException) {
        NetworkResult.Failure.InsecureConnection
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

private fun parseError(json: Json, response: Response<*>): NetworkResult.Failure {
    val raw = runCatching { response.errorBody()?.string() }.getOrNull()
    val parsed = raw?.let {
        runCatching { json.decodeFromString(ApiEnvelope.serializer(kotlinx.serialization.json.JsonElement.serializer()), it) }.getOrNull()
    }
    // Some errors don't fit the envelope (e.g. 429 sends "status": "fail"): still read their message.
    val loose = if (parsed == null && raw != null) runCatching { json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject }.getOrNull() else null
    fun textOf(o: kotlinx.serialization.json.JsonObject?, k: String) =
        (o?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
    val looseError = loose?.get("error") as? kotlinx.serialization.json.JsonObject
    return NetworkResult.Failure.Api(
        httpCode = response.code(),
        code = parsed?.error?.code ?: textOf(looseError, "code"),
        message = parsed?.error?.message ?: parsed?.message ?: textOf(looseError, "message") ?: textOf(loose, "message") ?: DEFAULT_ERROR,
    )
}

const val DEFAULT_ERROR = "Something went wrong. Please try again."

/**
 * For calls where only success matters and `data` may be null or anything.
 * Use this instead of `safeApiCall(allowEmptyData = true)` + `map`, which would cast Unit to the body type.
 */
suspend fun safeApiCallUnit(
    json: Json,
    authenticated: Boolean = true,
    call: suspend () -> Response<out ApiEnvelope<*>>,
): NetworkResult<Unit> {
    @Suppress("UNCHECKED_CAST")
    val r = safeApiCall(json, allowEmptyData = true, authenticated = authenticated) { call() as Response<ApiEnvelope<Any?>> }
    return when (r) {
        is NetworkResult.Success<*> -> NetworkResult.Success(Unit, r.message)
        is NetworkResult.Failure -> r
    }
}
