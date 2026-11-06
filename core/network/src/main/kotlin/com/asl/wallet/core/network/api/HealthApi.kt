package com.asl.wallet.core.network.api

import com.asl.wallet.core.network.interceptor.ApiHeaders
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers

/**
 * Used by the Foundation build's "Connection check": calls a protected endpoint without a token.
 * A 401 response proves the server is reachable, TLS is valid and the certificate pin matched.
 */
interface HealthApi {
    @Headers(ApiHeaders.NO_AUTH_VALUE)
    @GET("merchant/auth/me")
    suspend fun ping(): Response<JsonElement>
}
