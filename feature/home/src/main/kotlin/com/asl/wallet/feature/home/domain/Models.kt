package com.asl.wallet.feature.home.domain

import java.math.BigDecimal

data class WalletSummary(
    val balance: BigDecimal,
    val commission: BigDecimal,
    /** Epoch millis of the last successful fetch. */
    val updatedAt: Long,
)

enum class ServiceGroup { Banking, RechargeBills, More }

/** Visual family: picks the icon and gradient. */
enum class ServiceKind {
    Aeps, MicroAtm, MoneyTransfer, Upi, CardToCash, Bank, Mobile, Dth, Electricity, Bills, Fastag, Insurance,
    Pan, Aadhaar, Travel, Gift, Other,
}

sealed interface ServiceDestination {
    /** A screen in this app. [module] is the build module that delivers it. */
    data class App(val module: Int) : ServiceDestination
    /** External partner website, opened in a Custom Tab. Always https. */
    data class Partner(val url: String) : ServiceDestination
    /** Listed but not available in this app yet. */
    data object ComingSoon : ServiceDestination
}

data class HomeService(
    /** Stable key for pinning; derived from the name so it survives server id changes. */
    val key: String,
    val name: String,
    val kind: ServiceKind,
    val group: ServiceGroup,
    val destination: ServiceDestination,
    val description: String? = null,
)

data class LedgerEntry(
    val id: Long,
    val title: String,
    val date: String,
    val time: String,
    /** Positive for credit, negative for debit. */
    val amount: BigDecimal,
    val closing: BigDecimal?,
) {
    val isCredit: Boolean get() = amount.signum() >= 0
}

/** Last good Home data, kept encrypted on the phone for offline/cold start. */
data class HomeSnapshot(val wallet: WalletSummary?, val recent: List<LedgerEntry>)
