package com.asl.wallet.feature.reports.data

import com.asl.wallet.core.network.model.serviceRows
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.model.ApiEnvelope
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeApiCallUnit
import com.asl.wallet.feature.reports.domain.LedgerEntry
import com.asl.wallet.feature.reports.domain.LedgerType
import com.asl.wallet.feature.reports.domain.Period
import com.asl.wallet.feature.reports.domain.ReportKind
import com.asl.wallet.feature.reports.domain.ReportPage
import com.asl.wallet.feature.reports.domain.ReportRow
import com.asl.wallet.feature.reports.domain.ReportRules
import com.asl.wallet.feature.reports.domain.RowStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import com.asl.wallet.core.common.session.SignOutCleanup
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class LedgerQuery(val period: Period, val type: LedgerType = LedgerType.All, val search: String = "", val page: Int = 1, val limit: Int = ReportRules.PAGE)
data class ReportQuery(val period: Period, val status: RowStatus? = null, val search: String = "", val page: Int = 1)

interface ReportsRepository {
    suspend fun ledger(q: LedgerQuery): NetworkResult<ReportPage<LedgerEntry>>
    suspend fun ledgerEntry(id: Long): NetworkResult<LedgerEntry>
    suspend fun report(kind: ReportKind, q: ReportQuery): NetworkResult<ReportPage<ReportRow>>
    suspend fun complain(row: ReportRow, text: String): NetworkResult<Unit>
    /** Service reports this retailer has; all of them if the list can't be read. */
    suspend fun kinds(): Set<ReportKind>
    /** Every ledger entry in [period], oldest first, for the statement PDF. Stops at [ReportRules.STATEMENT_MAX]. */
    suspend fun statement(period: Period): NetworkResult<StatementData>
}

data class StatementData(val entries: List<LedgerEntry>, val truncated: Boolean)

@Singleton
class ReportsRepositoryImpl @Inject constructor(
    private val api: ReportsApi,
    private val json: Json,
    /** A Provider: the session owner depends on this repository's sign-out cleanup, so the user is read lazily. */
    private val currentUser: Provider<CurrentUser>,
) : ReportsRepository {

    /** Like safeApiCall, but keeps the envelope's meta and pagination (totals and paging). */
    private suspend fun <T> withEnvelope(call: suspend () -> Response<ApiEnvelope<T>>): Pair<NetworkResult<T>, ApiEnvelope<T>?> {
        var env: ApiEnvelope<T>? = null
        val r = safeApiCall(json) { call().also { env = it.body() } }
        return r to env
    }

    private fun canLoadMore(env: ApiEnvelope<*>?, size: Int, limit: Int): Boolean {
        env?.pagination?.let { return it.canLoadMore }
        return size >= limit
    }

    override suspend fun ledger(q: LedgerQuery): NetworkResult<ReportPage<LedgerEntry>> {
        val params = buildMap {
            put("fromDate", q.period.from.iso()); put("toDate", q.period.to.iso())
            put("tranType", q.type.query); put("page", q.page.toString()); put("limit", q.limit.toString()); put("sort", "desc")
            ReportRules.cleanSearch(q.search).trim().takeIf { it.length >= ReportRules.MIN_SEARCH }?.let { put("search", it) }
        }
        val (r, env) = withEnvelope { api.ledger(params) }
        return r.map { rows ->
            ReportPage(rows.mapNotNull(::readLedger), readSummary(null, env?.meta, env?.pagination?.totalOrNull), canLoadMore(env, rows.size, q.limit))
        }
    }

    override suspend fun ledgerEntry(id: Long): NetworkResult<LedgerEntry> = when (val r = safeApiCall(json) { api.ledgerEntry(id) }) {
        is NetworkResult.Success -> readLedger(r.data)?.let { NetworkResult.Success(it, r.message) } ?: NetworkResult.Failure.Unexpected("Unreadable entry")
        is NetworkResult.Failure -> r
    }

    override suspend fun report(kind: ReportKind, q: ReportQuery): NetworkResult<ReportPage<ReportRow>> {
        val params = mutableMapOf(
            "fromDate" to q.period.from.iso(), "toDate" to q.period.to.iso(),
            "page" to q.page.toString(), "limit" to ReportRules.PAGE.toString(),
        )
        if (kind == ReportKind.Dmt) {
            // Admin report filtered by reg no (user's choice, 6 Nov 2026): only ever the signed-in retailer's own.
            val regno = currentUser.get().user.value?.regno ?: return NetworkResult.Failure.Unauthorized
            params["regNo"] = regno.toString()
        }
        val status = q.status?.let(kind::statusValue)
        if (status != null && kind.statusParam != null) params[kind.statusParam] = status
        ReportRules.cleanSearch(q.search).trim().takeIf { it.length >= ReportRules.MIN_SEARCH }?.let { params[kind.searchParam(it)] = it }
        val (r, env) = withEnvelope { api.report(kind.path, params) }
        return r.map { rows ->
            ReportPage(rows.mapNotNull { readRow(kind, it) }, readSummary(kind, env?.meta, env?.pagination?.totalOrNull), canLoadMore(env, rows.size, ReportRules.PAGE))
        }
    }

    override suspend fun complain(row: ReportRow, text: String): NetworkResult<Unit> {
        val path = row.kind.complainPath ?: return NetworkResult.Failure.Unexpected("No complaints for this report")
        val clean = ReportRules.cleanComplaint(text.trim(), row.kind.complaintMax).trim()
        if (clean.isEmpty()) return NetworkResult.Failure.Unexpected("Empty complaint")
        return safeApiCallUnit(json) { api.complain(path, ComplainRequest(row.txnId, clean)) }
    }

    @Volatile private var kindsCache: Set<ReportKind>? = null

    override suspend fun kinds(): Set<ReportKind> {
        kindsCache?.let { return it }
        return when (val r = safeApiCall(json) { api.services() }) {
            is NetworkResult.Success -> kindsFromServices(serviceRows(r.data)).ifEmpty { ReportKind.entries.toSet() }.also { kindsCache = it }
            is NetworkResult.Failure -> ReportKind.entries.toSet()
        }
    }

    override suspend fun statement(period: Period): NetworkResult<StatementData> {
        val all = mutableListOf<LedgerEntry>()
        var page = 1
        while (true) {
            when (val r = ledger(LedgerQuery(period, page = page, limit = ReportRules.STATEMENT_PAGE))) {
                is NetworkResult.Failure -> return r
                is NetworkResult.Success -> {
                    all += r.data.items
                    if (all.size >= ReportRules.STATEMENT_MAX) return NetworkResult.Success(StatementData(all.take(ReportRules.STATEMENT_MAX).asReversed(), true), null)
                    if (!r.data.canLoadMore || r.data.items.isEmpty()) return NetworkResult.Success(StatementData(all.asReversed(), false), null)
                    page++
                }
            }
        }
    }

    /** Signing out clears the cached service list. */
    fun clear() { kindsCache = null }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ReportsModule {
    @Binds @Singleton abstract fun repository(impl: ReportsRepositoryImpl): ReportsRepository

    companion object {
        @dagger.Provides @Singleton
        fun reportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)

        /** The cached service list never outlives the session. */
        @dagger.Provides @IntoSet
        fun reportsCleanup(repo: ReportsRepositoryImpl): SignOutCleanup = SignOutCleanup { repo.clear() }
    }
}
