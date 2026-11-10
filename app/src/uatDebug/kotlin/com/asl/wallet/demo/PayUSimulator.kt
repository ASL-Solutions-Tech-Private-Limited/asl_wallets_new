package com.asl.wallet.demo

import android.app.Activity
import android.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.asl.wallet.feature.wallet.domain.PayUOrder
import com.asl.wallet.feature.wallet.payu.HashAsk
import com.asl.wallet.feature.wallet.payu.HashSource
import com.asl.wallet.feature.wallet.payu.PayUReturn
import com.asl.wallet.feature.wallet.payu.PayUStandIn
import kotlinx.coroutines.launch

/**
 * UAT debug only. Stands in for PayU's screens when no PayU test key is set (the order's key is
 * [AddMoneyDemoServer.NO_PAYU_KEY]). It does what the SDK would: asks the server for a hash through the real
 * hash API, lets the tester pick the outcome, and hands PayU's callback to the stand-in server.
 */
class PayUSimulator(private val server: AddMoneyDemoServer, private val log: DemoLog) : PayUStandIn {

    override fun handles(order: PayUOrder) = order.params["key"] == AddMoneyDemoServer.NO_PAYU_KEY

    override fun open(activity: Activity, order: PayUOrder, production: Boolean, hashes: HashSource, onReturn: (PayUReturn, String?) -> Unit) {
        val scope = (activity as? LifecycleOwner)?.lifecycleScope ?: return onReturn(PayUReturn.Error, null)
        val p = order.params
        scope.launch {
            log.add("PayU screens opened (simulated)", "No PayU test key in local.properties, so a simulated PayU screen stands in. With a test key, the real PayU test screens open here.")
            // What CheckoutPro asks for first: the payment options for this amount, signed by the server.
            val ask = HashAsk(
                txnId = order.txnId,
                hashName = "get_eligible_payment_options",
                hashString = "${p["key"]}|get_eligible_payment_options|{\"amount\":\"${p["amount"]}\",\"txnid\":\"${order.txnId}\",\"mobile_number\":\"${p["phone"].orEmpty()}\"}|",
            )
            log.add("SDK generateHash", "hashName=${ask.hashName} hashType=null postSalt=null\nhashString=${ask.hashString}")
            hashes.hash(ask)

            var done = false
            fun finish(r: PayUReturn, payId: String? = null) { if (!done) { done = true; onReturn(r, payId) } }
            fun pay(success: Boolean) {
                val form = server.simulatedCallback(order.txnId, success)
                if (form == null) { finish(PayUReturn.Error); return }
                val body = form.entries.joinToString("&") { (k, v) -> "$k=" + java.net.URLEncoder.encode(v, "UTF-8") }
                log.add(
                    if (success) "PayU → server: POST payments/payu/success (simulated)" else "PayU → server: POST payments/payu/failure (simulated)",
                    "What PayU posts to surl/furl (and the webhook). The server must check the reverse hash, call verify_payment, " +
                        "then credit the wallet once.\n```http\nPOST ${if (success) AddMoneyDemoServer.RETURN_URL else AddMoneyDemoServer.FAIL_URL}\nContent-Type: application/x-www-form-urlencoded\n\n$body\n```",
                )
                server.receiveCallback(form)
                log.add(if (success) "SDK onPaymentSuccess (simulated)" else "SDK onPaymentFailure (simulated)", "payuResponse=" + form.toString())
                finish(if (success) PayUReturn.Paid else PayUReturn.Finished, form["mihpayid"])
            }
            AlertDialog.Builder(activity)
                .setTitle("PayU checkout · simulated")
                .setMessage(
                    "₹${p["amount"]} · ${p["productinfo"]}\nTxn ${order.txnId}\n\n" +
                        "No PayU test key is set, so this stands in for PayU's screens. Choose what the customer does.",
                )
                .setCancelable(false)
                .setPositiveButton("Pay successfully") { _, _ -> pay(true) }
                .setNegativeButton("Payment fails") { _, _ -> pay(false) }
                .setNeutralButton("Cancel") { _, _ ->
                    log.add("SDK onPaymentCancel (simulated)", "isTxnInitiated=false · the customer closed PayU before paying")
                    finish(PayUReturn.Cancelled)
                }
                .show()
        }
    }
}
