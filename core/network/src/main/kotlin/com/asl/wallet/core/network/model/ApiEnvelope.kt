package com.asl.wallet.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Standard response wrapper of the ASL backend:
 * { "status": true, "statusCode": 200, "message": "...", "data": {...}, "error": {...}, "meta": {...}, "pagination": {...} }
 */
@Serializable
data class ApiEnvelope<T>(
    val status: Boolean = false,
    val statusCode: Int? = null,
    val message: String? = null,
    val data: T? = null,
    val error: ApiErrorBody? = null,
    val meta: JsonElement? = null,
    val pagination: Pagination? = null,
)

@Serializable
data class ApiErrorBody(val code: String? = null, val message: String? = null)

/**
 * The backend currently uses two pagination shapes (reports vs ledger and fund requests).
 * Both are accepted here, and [pageOrNull]/[totalPagesOrNull] normalise them.
 */
@Serializable
data class Pagination(
    val page: Int? = null,
    val currentPage: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val totalRecords: Int? = null,
    val totalPages: Int? = null,
    val hasNext: Boolean? = null,
    val hasPrevious: Boolean? = null,
) {
    val pageOrNull: Int? get() = page ?: currentPage
    val totalOrNull: Int? get() = total ?: totalRecords
    val totalPagesOrNull: Int? get() = totalPages
    val canLoadMore: Boolean
        get() = hasNext ?: run {
            val p = pageOrNull
            val tp = totalPagesOrNull
            p != null && tp != null && p < tp
        }
}
