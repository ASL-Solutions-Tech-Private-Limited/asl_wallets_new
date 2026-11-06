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
import javax.inject.Inject

/**
 * PayU CheckoutPro. The order fields come from the ASL server as they are. Static hashes the server already sent
 * (e.g. `payment_related_details_for_mobile_sdk`) are answered locally; any other hash is asked from the server.
 */
class PayUCheckoutProLauncher @Inject constructor() : PayUCheckout {

    override fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn) -> Unit) {
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
            .setUserCredential(p["user_credentials"] ?: p["userCredential"])
            .setAdditionalParams(additional)
            .build()

        val config = PayUCheckoutProConfig().apply {
            merchantName = "ASL Wallets"
            showExitConfirmationOnCheckoutScreen = true
            showExitConfirmationOnPaymentScreen = true
        }
        var done = false
        fun finish(r: PayUReturn) { if (!done) { done = true; onReturn(r) } }

        PayUCheckoutPro.open(activity, params, config, object : PayUCheckoutProListener {
            override fun onPaymentSuccess(response: Any) = finish(PayUReturn.Finished)
            override fun onPaymentFailure(response: Any) = finish(PayUReturn.Finished)
            override fun onPaymentCancel(isTxnInitiated: Boolean) = finish(if (isTxnInitiated) PayUReturn.Finished else PayUReturn.Cancelled)
            override fun onError(errorResponse: ErrorResponse) = finish(PayUReturn.Error)
            override fun setWebViewProperties(webView: WebView?, bank: Any?) = Unit

            override fun generateHash(valueMap: HashMap<String, String?>, hashGenerationListener: PayUHashGenerationListener) {
                val name = valueMap[PayUCheckoutProConstants.CP_HASH_NAME] ?: return
                val data = valueMap[PayUCheckoutProConstants.CP_HASH_STRING].orEmpty()
                (p[name] ?: if (name == "payment_hash") p["hash"] else null)?.takeIf { it.isNotBlank() }?.let { hashGenerationListener.onHashGenerated(hashMapOf(name to it)); return }
                val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return
                scope.launch {
                    // An empty answer makes PayU stop that step; it never gets a hash the server didn't make.
                    val h = runCatching { hashes.hash(order.txnId, name, data) }.getOrNull()
                    hashGenerationListener.onHashGenerated(hashMapOf(name to h))
                }
            }
        })
    }
}
