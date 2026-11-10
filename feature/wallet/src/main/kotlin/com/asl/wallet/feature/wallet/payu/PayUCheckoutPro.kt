package com.asl.wallet.feature.wallet.payu

import android.app.Activity
import android.webkit.WebView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.asl.wallet.feature.wallet.domain.PayUOrder
import com.payu.base.models.ErrorResponse
import com.payu.base.models.PayUPaymentParams
import com.payu.checkoutpro.PayUCheckoutPro
import com.payu.checkoutpro.models.PayUCheckoutProConfig
import com.payu.checkoutpro.utils.PayUCheckoutProConstants
import com.payu.ui.model.listeners.PayUCheckoutProListener
import com.payu.ui.model.listeners.PayUHashGenerationListener
import kotlinx.coroutines.launch
import java.util.Optional
import javax.inject.Inject

/**
 * PayU CheckoutPro. The order fields come from the ASL server as they are. Static hashes the server already sent
 * (e.g. `payment_related_details_for_mobile_sdk`) are answered locally; any other hash is asked from the server.
 */
class PayUCheckoutProLauncher @Inject constructor(
    private val events: Optional<PayUEvents>,
) : PayUCheckout {

    private fun log(event: String, detail: String) { if (events.isPresent) events.get().log(event, detail) }

    override fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn, String?) -> Unit) {
        val p = order.params
        val additional = HashMap<String, Any?>()
        (1..5).forEach { i -> p["udf$i"]?.let { additional["udf$i"] = it } }
        val params = PayUPaymentParams.Builder()
            .setAmount(p.getValue("amount"))
            .setIsProduction(production)
            .setKey(p.getValue("key"))
            .setProductInfo(p.getValue("productinfo"))
            .setPhone(p["phone"].orEmpty())
            .setTransactionId(order.txnId)
            .setFirstName(p.getValue("firstname"))
            .setEmail(p.getValue("email"))
            .setSurl(p.getValue("surl"))
            .setFurl(p.getValue("furl"))
            .setUserCredential(userCredentials(p))
            .setAdditionalParams(additional)
            .build()

        val config = PayUCheckoutProConfig().apply {
            merchantName = "ASL Wallets"
            showExitConfirmationOnCheckoutScreen = true
            showExitConfirmationOnPaymentScreen = true
        }
        var done = false
        fun finish(r: PayUReturn, mihpayid: String? = null) { if (!done) { done = true; onReturn(r, mihpayid) } }
        log("SDK open", "PayUCheckoutPro.open(isProduction=$production, txnid=${order.txnId}, amount=${p["amount"]})")

        PayUCheckoutPro.open(activity, params, config, object : PayUCheckoutProListener {
            override fun onPaymentSuccess(response: Any) { log("SDK onPaymentSuccess", payload(response)); finish(PayUReturn.Paid, mihpayid(response)) }
            override fun onPaymentFailure(response: Any) { log("SDK onPaymentFailure", payload(response)); finish(PayUReturn.Finished) }
            override fun onPaymentCancel(isTxnInitiated: Boolean) {
                log("SDK onPaymentCancel", "isTxnInitiated=$isTxnInitiated")
                finish(if (isTxnInitiated) PayUReturn.Finished else PayUReturn.Cancelled)
            }
            override fun onError(errorResponse: ErrorResponse) {
                log("SDK onError", "code=${errorResponse.errorCode} message=${errorResponse.errorMessage}")
                finish(PayUReturn.Error)
            }
            override fun setWebViewProperties(webView: WebView?, bank: Any?) = Unit

            override fun generateHash(valueMap: HashMap<String, String?>, hashGenerationListener: PayUHashGenerationListener) {
                val name = valueMap[PayUCheckoutProConstants.CP_HASH_NAME] ?: return
                val data = valueMap[PayUCheckoutProConstants.CP_HASH_STRING].orEmpty()
                val ask = HashAsk(
                    txnId = order.txnId, hashName = name, hashString = data,
                    postSalt = valueMap[PayUCheckoutProConstants.CP_POST_SALT]?.ifBlank { null },
                    hashType = valueMap[PayUCheckoutProConstants.CP_HASH_TYPE]?.ifBlank { null },
                )
                log("SDK generateHash", "hashName=$name hashType=${ask.hashType} postSalt=${ask.postSalt}\nhashString=$data")
                (p[name] ?: if (name == "payment_hash") p["hash"] else null)?.takeIf { it.isNotBlank() }?.let {
                    log("SDK hash answered from order", "hashName=$name (sent by the server with the order)")
                    hashGenerationListener.onHashGenerated(hashMapOf(name to it)); return
                }
                val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
                scope.launch {
                    // An empty answer makes PayU stop that step; it never gets a hash the server didn't make.
                    val h = runCatching { hashes.hash(ask) }.getOrNull()
                    hashGenerationListener.onHashGenerated(hashMapOf(name to h))
                }
            }
        })
    }

    /**
     * PayU's wallet and saved-payment options need "merchantKey:userId", or the SDK stops with
     * "user_credentials is missing!". The server's value wins; otherwise the same stable id for this retailer each
     * time: the regno (udf1), else the mobile number.
     */
    internal fun userCredentials(p: Map<String, String>): String? {
        p["user_credentials"]?.takeIf { it.isNotBlank() }?.let { return it }
        p["userCredential"]?.takeIf { it.isNotBlank() }?.let { return it }
        val key = p["key"]?.takeIf { it.isNotBlank() } ?: return null
        val id = p["udf1"]?.takeIf { it.isNotBlank() } ?: p["phone"]?.filter(Char::isDigit)?.takeIf { it.length >= 10 } ?: return null
        return "$key:$id"
    }

    /** PayU's payment id from the SDK result, if present (digits and letters only). */
    private fun mihpayid(response: Any): String? {
        val raw = (response as? Map<*, *>)?.get(PayUCheckoutProConstants.CP_PAYU_RESPONSE)?.toString() ?: return null
        // PayU calls it "mihpayid" on surl/furl and "id" in the CheckoutPro result (seen live, 9 Nov 2026).
        return Regex("\"(?:mihpayid|id)\"\\s*:\\s*\"?([A-Za-z0-9]{1,40})").find(raw)?.groupValues?.get(1)
    }

    /** PayU hands back a map with `payuResponse` (what PayU also posts to surl/furl) and `merchantResponse`. */
    private fun payload(response: Any): String = (response as? Map<*, *>)?.let { m ->
        "payuResponse=${m[PayUCheckoutProConstants.CP_PAYU_RESPONSE]}\nmerchantResponse=${m[PayUCheckoutProConstants.CP_MERCHANT_RESPONSE]}"
    } ?: response.toString()
}
