package com.asl.wallet.core.network.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * `merchant/services` rows. The server answered with a plain list until early Nov 2026 and now with
 * `{totalServices, services: [...]}` (seen on UAT 9 Nov 2026); both are read, so neither change breaks the app.
 */
fun serviceRows(data: JsonElement?): List<JsonObject> {
    val arr = when (data) {
        is JsonArray -> data
        is JsonObject -> (data["services"] ?: data["data"]) as? JsonArray
        else -> null
    }
    return arr?.mapNotNull { it as? JsonObject }.orEmpty()
}
