package com.asl.wallet.feature.aeps.data

import com.asl.wallet.feature.aeps.domain.AepsConfig
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.AgentStatus
import com.asl.wallet.feature.aeps.domain.RdDevice
import javax.inject.Inject
import javax.inject.Singleton

/** The retailer's own Aadhaar, mobile, email and PAN, from `auth/me`. Memory only; wiped on sign-out. */
data class AgentIdentity(
    val aadhaar: String, val mobile: String, val email: String, val pan: String,
    /** Profile values used only to prefill onboarding when the bank has none. */
    val firstName: String = "", val lastName: String = "", val address: String = "", val city: String = "", val pincode: String = "", val shopName: String = "",
)

/**
 * In-memory state shared by the AEPS screens for one AEPS visit: chosen bank, config, status, scanner.
 * Nothing here is written to disk. [clear] runs on every sign-out.
 */
@Singleton
class AepsSession @Inject constructor() {
    @Volatile var provider: AepsProvider? = null
    @Volatile var config: AepsConfig? = null
    @Volatile var status: AgentStatus? = null
    @Volatile var identity: AgentIdentity? = null
    @Volatile var device: RdDevice? = null

    /** "Same customer again": kept only for this process, never saved. */
    @Volatile var lastCustomer: LastCustomer? = null

    data class LastCustomer(val aadhaar: String, val mobile: String, val bankIin: String)

    fun clear() { provider = null; config = null; status = null; identity = null; device = null; lastCustomer = null }
}
