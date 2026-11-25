package com.asl.wallet.feature.matm.domain

import java.math.BigDecimal

/**
 * Micro-ATM transaction type. [code] goes to the backend (`txn_type`); [initType] is what the
 * `initiate` response carries back and what the FingPay SDK expects (2 = withdrawal, 4 = balance).
 */
enum class MatmTxnType(val code: String, val initType: Int) {
    Withdraw("CW", 2),
    Balance("BE", 4);

    companion object {
        fun ofInitType(type: Int?): MatmTxnType = if (type == 4) Balance else Withdraw
    }
}

/** A real device fix. mATM is geo-tagged; no fallback coordinates. */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * What `merchant/matm/initiate` returns: the credentials + parameters the FingPay SDK needs to
 * start a card read. Fields mirror the old app's `InitiateMATMResponse` 1:1.
 */
data class MatmInit(
    val superMerchantId: String,
    val merchantUserId: String,
    val merchantPassword: String,
    val mobileNumber: String,
    val amount: String,
    val remarks: String,
    val txnId: String,
    val imei: String,
    val amountEditable: Boolean,
    val type: MatmTxnType,
)

/**
 * The FingPay SDK's result, read back from the returned intent. Also the exact body posted to
 * `merchant/matm/update-status`. Card number is masked by the SDK; never log it.
 */
data class MatmResult(
    val status: Boolean,
    val message: String,
    val transAmount: Double,
    val balanceAmount: Double,
    val bankRrn: String,
    val transType: String,
    val type: Int,
    val cardNum: String,
    val bankName: String,
    val cardType: String,
    val terminalId: String,
    val txnId: String,
    val mobileNumber: String,
    val fpId: String,
    val errorCode: String,
) {
    val amount: BigDecimal get() = runCatching { BigDecimal.valueOf(transAmount) }.getOrDefault(BigDecimal.ZERO)
    val balance: BigDecimal get() = runCatching { BigDecimal.valueOf(balanceAmount) }.getOrDefault(BigDecimal.ZERO)
}

/** The server's confirmation of an update-status post. [commission] / [finalStatus] are shown if present. */
data class MatmConfirm(
    val ok: Boolean,
    val message: String?,
    val commission: BigDecimal?,
    val finalStatus: String?,
)
