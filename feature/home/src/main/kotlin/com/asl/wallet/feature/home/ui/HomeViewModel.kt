package com.asl.wallet.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.feature.home.data.HomeRepository
import com.asl.wallet.feature.home.domain.HomeService
import com.asl.wallet.feature.home.domain.LedgerEntry
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.home.domain.ServiceDestination
import com.asl.wallet.feature.home.domain.ServiceGroup
import com.asl.wallet.feature.home.domain.ServiceKind
import com.asl.wallet.feature.home.domain.WalletSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServiceSheet {
    val service: HomeService
    data class Partner(override val service: HomeService, val url: String) : ServiceSheet
    data class ComingSoon(override val service: HomeService) : ServiceSheet
}

sealed interface HomeEvent {
    data class OpenPartner(val url: String) : HomeEvent
    data class OpenModule(val module: Int, val serviceKey: String) : HomeEvent
}

data class HomeUiState(
    val user: UserSummary? = null,
    val wallet: WalletSummary? = null,
    /** First load with nothing saved: show skeletons. */
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Last refresh failed for lack of internet. Cached data is shown, money actions are paused. */
    val offline: Boolean = false,
    /** Balance could not be loaded and nothing is saved. */
    val walletError: Boolean = false,
    val balanceHidden: Boolean = false,
    val services: List<HomeService> = emptyList(),
    val servicesFromServer: Boolean = false,
    val pinned: List<HomeService> = emptyList(),
    val recent: List<LedgerEntry> = emptyList(),
    val recentLoading: Boolean = true,
    val recentError: Boolean = false,
    val sheet: ServiceSheet? = null,
    val query: String = "",
    /** Keys being edited on "Edit Home services"; null when not editing. */
    val editSelection: List<String>? = null,
) {
    val searchResults: List<HomeService> get() = ServiceCatalogue.search(services, query)
    fun grouped(list: List<HomeService> = searchResults): Map<ServiceGroup, List<HomeService>> =
        ServiceGroup.entries.associateWith { g -> list.filter { it.group == g } }.filterValues { it.isNotEmpty() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val currentUser: CurrentUser,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(user = currentUser.user.value))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var savedPins: List<String>? = null
    private var hideJob: Job? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch { currentUser.user.collect { u -> _state.update { it.copy(user = u) } } }
        viewModelScope.launch {
            currentUser.user.map { it?.regno }.distinctUntilChanged()
                .flatMapLatest { regno -> if (regno == null) emptyFlow() else repo.pins(regno) }
                .collect { keys -> savedPins = keys; repin() }
        }
        viewModelScope.launch {
            repo.cached()?.let { snap ->
                _state.update { s ->
                    s.copy(
                        wallet = s.wallet ?: snap.wallet,
                        loading = s.wallet == null && snap.wallet == null && s.loading,
                        recent = s.recent.ifEmpty { snap.recent },
                    )
                }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (!initial) _state.update { it.copy(refreshing = true) }
            val wallet = async { repo.wallet() }
            val recent = async { repo.recent() }
            val services = async { repo.services() }

            val sv = services.await()
            _state.update { it.copy(services = sv.services, servicesFromServer = sv.fromServer) }
            repin()

            val w = wallet.await()
            val offline = w is NetworkResult.Failure.NoInternet
            _state.update { s ->
                when (w) {
                    is NetworkResult.Success -> s.copy(wallet = w.data, walletError = false, offline = false, loading = false)
                    is NetworkResult.Failure -> s.copy(walletError = s.wallet == null, offline = offline, loading = false)
                }
            }
            if (w is NetworkResult.Success && !_state.value.balanceHidden) scheduleAutoHide()

            val r = recent.await()
            _state.update { s ->
                when (r) {
                    is NetworkResult.Success -> s.copy(recent = r.data, recentError = false, recentLoading = false)
                    is NetworkResult.Failure -> s.copy(recentError = s.recent.isEmpty(), recentLoading = false)
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun toggleBalance() {
        val hide = !_state.value.balanceHidden
        _state.update { it.copy(balanceHidden = hide) }
        if (hide) hideJob?.cancel() else scheduleAutoHide()
    }

    /** The balance hides itself 10 s after it was shown, so it isn't left on screen at the counter. */
    private fun scheduleAutoHide() {
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(AUTO_HIDE_MS)
            _state.update { it.copy(balanceHidden = true) }
        }
    }

    fun onServiceClick(service: HomeService) {
        val sheet: ServiceSheet? = when (val d = service.destination) {
            is ServiceDestination.Partner -> ServiceSheet.Partner(service, d.url)
            ServiceDestination.ComingSoon -> ServiceSheet.ComingSoon(service)
            is ServiceDestination.App -> {
                _events.trySend(HomeEvent.OpenModule(d.module, service.key)); null
            }
        }
        _state.update { it.copy(sheet = sheet) }
    }

    /** Distributor network tools and wallet shortcuts that arrive in later modules. */
    fun onComingSoon(name: String) {
        _state.update { it.copy(sheet = ServiceSheet.ComingSoon(HomeService(ServiceCatalogue.keyOf(name), name, ServiceKind.Other, ServiceGroup.More, ServiceDestination.ComingSoon))) }
    }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    fun confirmPartner() {
        val sheet = _state.value.sheet as? ServiceSheet.Partner ?: return
        // Re-checked at the last moment: only a clean https URL ever leaves the app.
        val url = ServiceCatalogue.safePartnerUrl(sheet.url)
        _state.update { it.copy(sheet = null) }
        if (url != null) _events.trySend(HomeEvent.OpenPartner(url))
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q.take(40)) }

    // ---- Edit Home services ----
    fun startEdit() = _state.update { it.copy(editSelection = it.pinned.map(HomeService::key)) }

    fun toggleEdit(key: String) = _state.update { s ->
        val sel = s.editSelection ?: return@update s
        val next = when {
            key in sel -> sel - key
            sel.size >= ServiceCatalogue.MAX_PINNED -> sel
            else -> sel + key
        }
        s.copy(editSelection = next)
    }

    fun resetEdit() = _state.update { s -> s.copy(editSelection = ServiceCatalogue.pinned(s.services, null).map(HomeService::key)) }

    fun saveEdit() {
        val sel = _state.value.editSelection ?: return
        val regno = _state.value.user?.regno ?: return
        _state.update { it.copy(editSelection = null) }
        savedPins = sel
        repin()
        viewModelScope.launch { repo.savePins(regno, sel) }
    }

    fun cancelEdit() = _state.update { it.copy(editSelection = null) }

    fun logout() { viewModelScope.launch { currentUser.logout() } }

    private fun repin() = _state.update { it.copy(pinned = ServiceCatalogue.pinned(it.services, savedPins)) }

    companion object { const val AUTO_HIDE_MS = 10_000L }
}
