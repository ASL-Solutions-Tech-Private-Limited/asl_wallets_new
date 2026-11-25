package com.asl.wallet.feature.matm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.feature.matm.R
import com.asl.wallet.feature.matm.data.LocationSource
import com.asl.wallet.feature.matm.data.MatmDeviceResult
import com.asl.wallet.feature.matm.data.MatmRepository
import com.asl.wallet.feature.matm.domain.GeoPoint
import com.asl.wallet.feature.matm.domain.MatmConfirm
import com.asl.wallet.feature.matm.domain.MatmInit
import com.asl.wallet.feature.matm.domain.MatmResult
import com.asl.wallet.feature.matm.domain.MatmTxnType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** A one-shot request for the screen to launch the card reader, then call [MatmViewModel.launchConsumed]. */
data class LaunchRequest(val init: MatmInit, val at: GeoPoint?)

data class MatmForm(
    val type: MatmTxnType = MatmTxnType.Withdraw,
    val mobile: String = "",
    val amount: String = "",
    val mobileError: Int? = null,
    val amountError: Int? = null,
)

/** The completed transaction shown as a receipt. [serverWarning] = the bank result is in, but the server didn't confirm. */
data class MatmReceipt(val result: MatmResult, val confirm: MatmConfirm?, val serverWarning: Boolean)

data class MatmState(
    val form: MatmForm = MatmForm(),
    val busy: Boolean = false,
    /** A transient banner from the server (already-resolved text). */
    val banner: String? = null,
    /** A transient banner from the app (a string resource, e.g. "cancelled"). */
    val bannerRes: Int? = null,
    val launch: LaunchRequest? = null,
    val receipt: MatmReceipt? = null,
)

@HiltViewModel
class MatmViewModel @Inject constructor(
    private val repo: MatmRepository,
    private val location: LocationSource,
) : ViewModel() {

    private val _state = MutableStateFlow(MatmState())
    val state: StateFlow<MatmState> = _state.asStateFlow()

    val hasLocationPermission: Boolean get() = location.hasPermission()

    /**
     * One key per transaction attempt: reused when the retailer retries the SAME transaction (so the
     * backend de-duplicates), and regenerated whenever the inputs change (a genuinely new transaction).
     */
    private var attemptKey: IdempotencyKey = IdempotencyKey.new()

    fun setType(type: MatmTxnType) { attemptKey = IdempotencyKey.new(); _state.update { it.copy(form = it.form.copy(type = type, amountError = null), banner = null, bannerRes = null) } }
    fun setMobile(v: String) { attemptKey = IdempotencyKey.new(); _state.update { it.copy(form = it.form.copy(mobile = v.filter(Char::isDigit).take(10), mobileError = null), banner = null, bannerRes = null) } }
    fun setAmount(v: String) { attemptKey = IdempotencyKey.new(); _state.update { it.copy(form = it.form.copy(amount = v.filter(Char::isDigit).take(6), amountError = null), banner = null, bannerRes = null) } }

    fun clearBanner() = _state.update { it.copy(banner = null, bannerRes = null) }
    fun launchConsumed() = _state.update { it.copy(launch = null) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        val f = s.form
        val mobileOk = f.mobile.length == 10 && f.mobile.first() != '0'
        val amount = f.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val amountOk = f.type != MatmTxnType.Withdraw || amount.signum() > 0
        if (!mobileOk || !amountOk) {
            _state.update {
                it.copy(form = f.copy(
                    mobileError = R.string.m_err_mobile.takeIf { !mobileOk },
                    amountError = R.string.m_err_amount.takeIf { !amountOk },
                ))
            }
            return
        }
        _state.update { it.copy(busy = true, banner = null, bannerRes = null) }
        viewModelScope.launch {
            val at = runCatching { location.current() }.getOrNull()
            when (val r = repo.initiate(f.type, f.mobile, amount, attemptKey)) {
                is NetworkResult.Success -> _state.update { it.copy(busy = false, launch = LaunchRequest(r.data, at)) }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, banner = errorText(r)) }
            }
        }
    }

    fun onDeviceResult(result: MatmDeviceResult) {
        when (result) {
            is MatmDeviceResult.Cancelled -> _state.update { it.copy(busy = false, bannerRes = R.string.m_cancelled) }
            is MatmDeviceResult.Ok -> post(result.result)
        }
    }

    private fun post(deviceResult: MatmResult) {
        _state.update { it.copy(busy = true, banner = null, bannerRes = null) }
        viewModelScope.launch {
            // The bank result is authoritative for the receipt; the server call just records it.
            when (val r = repo.updateStatus(deviceResult)) {
                is NetworkResult.Success -> _state.update { it.copy(busy = false, receipt = MatmReceipt(deviceResult, r.data, serverWarning = false)) }
                is NetworkResult.Failure -> _state.update { it.copy(busy = false, receipt = MatmReceipt(deviceResult, null, serverWarning = true)) }
            }
        }
    }

    /** Start another transaction (keeps the chosen type; fresh key for the new transaction). */
    fun again() { attemptKey = IdempotencyKey.new(); _state.update { MatmState(form = MatmForm(type = it.form.type)) } }
}

/** Human text for a failed call. */
internal fun errorText(f: NetworkResult.Failure): String = when (f) {
    is NetworkResult.Failure.Api -> f.message
    NetworkResult.Failure.NoInternet -> "No internet connection."
    NetworkResult.Failure.Timeout -> "The request timed out. Try again."
    NetworkResult.Failure.Unauthorized -> "Session expired. Please sign in again."
    NetworkResult.Failure.InsecureConnection -> "Insecure connection. Try again on a trusted network."
    is NetworkResult.Failure.Unexpected -> f.reason
}
