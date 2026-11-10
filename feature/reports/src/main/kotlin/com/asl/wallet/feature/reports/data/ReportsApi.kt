package com.asl.wallet.feature.reports.data

import com.asl.wallet.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.QueryMap
import retrofit2.http.Url

/**
 * Report endpoints (checked read-only on UAT, 6 Nov 2026). Service reports share one call: [path] always comes
 * from [com.asl.wallet.feature.reports.domain.ReportKind] constants, never from input.
 * The app never sends `regno` to the ledger (the server wrongly honours it: reported); for the DMT report it sends
 * only the signed-in retailer's own regNo.
 */
interface ReportsApi {
    @GET("merchant/ledger-report")
    suspend fun ledger(@QueryMap query: Map<String, String>): Response<ApiEnvelope<List<JsonObject>>>

    @GET("merchant/ledger-report/{id}")
    suspend fun ledgerEntry(@Path("id") id: Long): Response<ApiEnvelope<JsonObject>>

    @GET
    suspend fun report(@Url path: String, @QueryMap query: Map<String, String>): Response<ApiEnvelope<List<JsonObject>>>

    @POST
    suspend fun complain(@Url path: String, @Body body: ComplainRequest): Response<ApiEnvelope<JsonElement>>

    @GET("merchant/services")
    suspend fun services(): Response<ApiEnvelope<JsonElement>>
}

@Serializable data class ComplainRequest(val servid: String, val complainText: String)
