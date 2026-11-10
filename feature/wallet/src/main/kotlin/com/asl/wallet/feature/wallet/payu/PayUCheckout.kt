package com.asl.wallet.feature.wallet.payu

import android.app.Activity
import com.asl.wallet.feature.wallet.domain.PayUOrder

/** Per-flavor switches, supplied by the app module. */
data class WalletConfig(
    /** false until the backend returns the PayU SDK fields: "Pay" then shows the "opens soon" sheet (4.11). */
    val payuEnabled: Boolean,
    /** PayU environment comes from the build flavor, never from the server. */
    val payuProduction: Boolean,
)

/**
 * What PayU's screens said when they closed. The app never trusts this; it always asks the ASL server.
 * - [Paid]: PayU reported a successful payment (only then does the app ask the server to verify).
 * - [Finished]: a payment was attempted but failed or was cancelled after starting (no verify; status only).
 * - [Cancelled]: closed before any payment started. [Error]: PayU could not run.
 */
enum class PayUReturn { Paid, Finished, Cancelled, Error }

/**
 * Opens PayU's own payment screens for an order the ASL server created. Every hash PayU asks for comes from
 * [hashes] (made on the server); the PayU salt is never in the app.
 */
interface PayUCheckout {
    /** [onReturn] also gets PayU's payment id when it reported one (a hint for the server's verify, never proof). */
    fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn, String?) -> Unit)
}

/** What PayU asks to be signed. [postSalt] and [hashType] come from the SDK when it sends them (else null). */
data class HashAsk(val txnId: String, val hashName: String, val hashString: String, val postSalt: String? = null, val hashType: String? = null)

/** Gives PayU the hash it asks for, from the ASL server. Returns null if the server can't make it. */
fun interface HashSource {
    suspend fun hash(ask: HashAsk): String?
}

/**
 * UAT debug builds only: a stand-in for PayU's screens when no PayU test account is set up, so the whole
 * Add Money flow can still be shown. Release and live builds have none (Hilt gives an empty Optional).
 */
interface PayUStandIn : PayUCheckout {
    fun handles(order: PayUOrder): Boolean
}

/** UAT debug builds only: records what the PayU SDK did (hash asks, results) for the demo log. */
fun interface PayUEvents {
    fun log(event: String, detail: String)
}
