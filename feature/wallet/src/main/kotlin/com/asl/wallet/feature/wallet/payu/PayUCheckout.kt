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

/** What PayU's screens said when they closed. The app never trusts this; it always asks the ASL server. */
enum class PayUReturn { Finished, Cancelled, Error }

/**
 * Opens PayU's own payment screens for an order the ASL server created. Every hash PayU asks for comes from
 * [hashes] (made on the server); the PayU salt is never in the app.
 */
interface PayUCheckout {
    fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn) -> Unit)
}

/** Gives PayU the hash it asks for, from the ASL server. Returns null if the server can't make it. */
fun interface HashSource {
    suspend fun hash(txnId: String, hashName: String, hashString: String): String?
}
