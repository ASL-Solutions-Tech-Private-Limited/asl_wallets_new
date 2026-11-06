package com.asl.wallet.ui

import java.io.IOException
import javax.net.ssl.SSLException

/** Outcome of the Foundation build's "Check secure connection" (calls a protected endpoint without a token). */
sealed interface ConnectionResult {
    data object Checking : ConnectionResult
    /** 401 = server reachable, TLS valid and the certificate pin matched (pinning failures never reach HTTP). */
    data class Verified(val millis: Long) : ConnectionResult
    data object InsecureBlocked : ConnectionResult
    data object NoInternet : ConnectionResult
    data class Unexpected(val detail: String) : ConnectionResult

    companion object {
        fun from(httpCode: Int?, error: Throwable?, millis: Long): ConnectionResult = when {
            error is SSLException -> InsecureBlocked
            error is IOException -> NoInternet
            error != null -> Unexpected(error::class.java.simpleName)
            httpCode == 401 || httpCode in 200..299 -> Verified(millis)
            else -> Unexpected("HTTP $httpCode")
        }
    }
}
