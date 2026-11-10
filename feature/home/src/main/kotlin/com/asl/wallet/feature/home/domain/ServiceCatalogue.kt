package com.asl.wallet.feature.home.domain

import com.asl.wallet.feature.home.data.ServiceDto
import com.asl.wallet.feature.home.data.flag
import java.net.URI

/**
 * Turns the server's service list into what Home shows: kind, group and where a tap goes.
 * KYC is not a Home concern: it is part of AEPS onboarding (Module 3), so nothing is locked here.
 * Pure Kotlin so every rule is unit-tested.
 */
object ServiceCatalogue {

    /** Build modules whose screens exist in this app. A service becomes tappable when its module ships. */
    val BUILT_MODULES: Set<Int> = setOf(AEPS_MODULE, PAYOUT_MODULE, RECHARGE_MODULE, DMT_MODULE, BBPS_MODULE)

    /** AEPS (with its own onboarding/eKYC) ships in Module 3. */
    const val AEPS_MODULE = 3

    /** Payout = send to the retailer's own bank account, built in the Wallet (Module 4). */
    const val PAYOUT_MODULE = 4

    /** Mobile prepaid and DTH recharge ship in Module 5A. Postpaid isn't offered (user, 4 Nov 2026). */
    const val RECHARGE_MODULE = 5

    /** Money transfer (DMT) ships in Module 5B. */
    const val DMT_MODULE = 8

    /** BBPS (Bharat Connect) bill payment: electricity, Fastag, gas, postpaid, loan repayment … (9 Nov 2026). */
    const val BBPS_MODULE = 10

    /** The single, merged AEPS tile's key. */
    const val AEPS_KEY = "aeps"

    const val MAX_PINNED = 7 // + the fixed "All services" tile = 8

    private val DEFAULT_PIN_KINDS = listOf(
        ServiceKind.Aeps, ServiceKind.MicroAtm, ServiceKind.MoneyTransfer, ServiceKind.Upi,
        ServiceKind.Mobile, ServiceKind.Bills, ServiceKind.CardToCash,
    )

    /** Services that exist on the server but are switched off in the app for now (user, 4 Nov 2026). */
    private val HIDDEN = listOf("aadhaar pay", "aadhar pay", "aadhaarpay", "adhaar pay")
    fun isHidden(name: String): Boolean = name.lowercase().let { n -> HIDDEN.any { n.contains(it) } }

    /** Kind and the module that builds it in-app (null = no in-app screen planned; partner site or nothing). */
    fun classify(name: String): Pair<ServiceKind, Int?> {
        val n = " " + name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() + " "
        fun has(vararg w: String) = w.any { n.contains(it) }
        return when {
            has("micro atm", "matm", "mini atm") -> ServiceKind.MicroAtm to 7
            has("aeps", "aadhaar enabled") -> ServiceKind.Aeps to AEPS_MODULE
            has("dmt", "money transfer", "domestic", "remit") -> ServiceKind.MoneyTransfer to DMT_MODULE
            has("payout", "settlement", "send to bank") -> ServiceKind.MoneyTransfer to PAYOUT_MODULE
            has("card to cash", " cc ", "credit card") -> ServiceKind.CardToCash to 9
            has(" upi", " qr ", " qr") -> ServiceKind.Upi to 9
            has(" csp ", " bc ", "bank", "kiosk") -> ServiceKind.Bank to null
            has(" dth ") -> ServiceKind.Dth to RECHARGE_MODULE
            has("postpaid") -> ServiceKind.Mobile to BBPS_MODULE
            has("mobile", "recharge", "prepaid") -> ServiceKind.Mobile to RECHARGE_MODULE
            has("electric", "power") -> ServiceKind.Electricity to BBPS_MODULE
            has("fastag") -> ServiceKind.Fastag to BBPS_MODULE
            has("bbps", "bill", " gas ", " lpg ", "water", "broadband", "landline") -> ServiceKind.Bills to BBPS_MODULE
            has(" lic ", "insurance") -> ServiceKind.Insurance to 11
            has(" pan ", "nsdl", " uti ") -> ServiceKind.Pan to 11
            has("aadhaar", "aadhar") -> ServiceKind.Aadhaar to null
            has("train", "irctc", "flight", " bus ", "hotel", "travel", " air ") -> ServiceKind.Travel to null
            has("gift", "voucher", "google play") -> ServiceKind.Gift to 11
            // Loan repayment is a BBPS category (old app's 3rd dashboard).
            has("loan") -> ServiceKind.Bills to BBPS_MODULE
            else -> ServiceKind.Other to null
        }
    }

    fun groupOf(kind: ServiceKind): ServiceGroup = when (kind) {
        ServiceKind.Aeps, ServiceKind.MicroAtm, ServiceKind.MoneyTransfer, ServiceKind.Upi, ServiceKind.CardToCash, ServiceKind.Bank -> ServiceGroup.Banking
        ServiceKind.Mobile, ServiceKind.Dth, ServiceKind.Electricity, ServiceKind.Bills, ServiceKind.Fastag, ServiceKind.Insurance -> ServiceGroup.RechargeBills
        else -> ServiceGroup.More
    }

    fun keyOf(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Partner links must be plain https with a host. Anything else (http, javascript:, intent:, file:,
     * user-info tricks like https://asl@evil.com) is rejected and the service shows "Coming soon".
     */
    fun safePartnerUrl(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.length > 2048) return null
        val uri = runCatching { URI(s) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host ?: return null
        if (uri.rawUserInfo != null || host.isBlank() || !host.contains('.')) return null
        return uri.toString()
    }

    fun fromServer(items: List<ServiceDto>, built: Set<Int> = BUILT_MODULES): List<HomeService> = items
        .asSequence()
        .filter { it.status.flag() != false }
        .filter { !isHidden(it.services_name.orEmpty()) }
        .mapNotNull { dto ->
            val name = dto.services_name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val (kind, module) = classify(name)
            val destination = when {
                module != null && module in built -> ServiceDestination.App(module)
                module != null -> ServiceDestination.ComingSoon
                else -> safePartnerUrl(dto.services_link)?.let { ServiceDestination.Partner(it) } ?: ServiceDestination.ComingSoon
            }
            (dto.service_order ?: Int.MAX_VALUE) to HomeService(
                key = keyOf(name),
                name = name,
                kind = kind,
                group = groupOf(kind),
                destination = destination,
                description = dto.service_desc?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        .sortedWith(compareBy({ it.first }, { it.second.name.lowercase() }))
        .map { it.second }
        // One AEPS tile, however many banks the server lists ("AEPS (YES BANK)", "AEPS (KOTAK BANK)"):
        // the AEPS flow asks for the bank itself (old app: one AEPS tile, 10 Nov 2026).
        .map { if (it.kind == ServiceKind.Aeps && it.destination is ServiceDestination.App) it.copy(key = AEPS_KEY, name = "AEPS", description = null) else it }
        .distinctBy { it.key }
        .toList()

    /**
     * Payout (send to the retailer's own bank) is a wallet feature every retailer has, but the server's service list
     * doesn't name it (UAT, 9 Nov 2026): it is added after the server's services when missing.
     */
    fun withWalletServices(list: List<HomeService>, built: Set<Int> = BUILT_MODULES): List<HomeService> =
        if (list.any { it.destination == ServiceDestination.App(PAYOUT_MODULE) }) list
        else list + fromServer(listOf(ServiceDto(id = -1, services_name = "Payout", service_order = Int.MAX_VALUE)), built)

    /** Used when `merchant/services` fails or is empty. No partner URLs are invented here. */
    fun fallback(built: Set<Int> = BUILT_MODULES): List<HomeService> = fromServer(
        listOf(
            "AEPS", "Micro ATM", "Money transfer", "UPI transfer", "Card to cash", "Payout",
            "Mobile recharge", "DTH recharge", "Electricity bill", "BBPS bill pay", "FASTag", "LIC premium",
            "PAN card", "Aadhaar services", "Train tickets", "Flight tickets", "Gift cards", "Loans",
        ).mapIndexed { i, n -> ServiceDto(id = i.toLong(), services_name = n, service_order = i) },
        built,
    )

    /** Saved pins that still exist, in saved order; otherwise one service of each default kind. */
    fun pinned(all: List<HomeService>, savedKeys: List<String>?): List<HomeService> {
        val byKey = all.associateBy { it.key }
        val saved = savedKeys.orEmpty().mapNotNull { byKey[it] }.distinct().take(MAX_PINNED)
        if (saved.isNotEmpty()) return saved
        return DEFAULT_PIN_KINDS.mapNotNull { k -> all.firstOrNull { it.kind == k } }.take(MAX_PINNED)
    }

    /** Case-insensitive search on name and description. */
    fun search(all: List<HomeService>, query: String): List<HomeService> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.name.lowercase().contains(q) || it.description?.lowercase()?.contains(q) == true }
    }
}
