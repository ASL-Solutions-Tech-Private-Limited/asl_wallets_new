package com.asl.wallet.feature.profile.data

import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeApiCallUnit
import com.asl.wallet.feature.profile.domain.ActiveService
import com.asl.wallet.feature.profile.domain.Commission
import com.asl.wallet.feature.profile.domain.CommissionRow
import com.asl.wallet.feature.profile.domain.CommissionSection
import com.asl.wallet.feature.profile.domain.OtpTarget
import com.asl.wallet.feature.profile.domain.ProfileDetails
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    suspend fun profile(): NetworkResult<ProfileDetails>
    suspend fun commission(): NetworkResult<Commission>
    suspend fun activeServices(): NetworkResult<List<ActiveService>>
    suspend fun requestPasswordChange(current: String, new: String): NetworkResult<OtpTarget>
    suspend fun confirmPasswordChange(otp: String): NetworkResult<Unit>
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val api: ProfileApi,
    private val json: Json,
) : ProfileRepository {

    override suspend fun profile() = safeApiCall(json) { api.me() }.map { it.toDetails() }

    override suspend fun commission() = safeApiCall(json) { api.commission() }.map { it.toCommission() }

    override suspend fun activeServices() = safeApiCall(json) { api.services() }.map { list ->
        list.mapNotNull { it.toActive() }.distinctBy { it.name.lowercase() }.sortedWith(compareBy({ !it.active }, { it.name.lowercase() }))
    }

    override suspend fun requestPasswordChange(current: String, new: String) =
        safeApiCall(json) { api.requestChangePasswordOtp(ChangePasswordRequest(current, new, new)) }
            .map { OtpTarget(it.sentTo?.takeIf(String::isNotBlank) ?: "your registered contact", it.channel.orEmpty()) }

    override suspend fun confirmPasswordChange(otp: String) =
        safeApiCallUnit(json) { api.verifyChangePassword(VerifyChangePasswordRequest(otp)) }
}

internal fun ProfileDto.toDetails(): ProfileDetails {
    val name = listOfNotNull(fname?.trim(), lname?.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifBlank { "Retailer" }
    return ProfileDetails(
        name = name,
        initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
        regno = regno,
        shopName = company_name?.trim().orEmpty(),
        mobileMasked = Masking.mobile(phone),
        emailMasked = if (email.isNullOrBlank()) "—" else Masking.email(email),
        panMasked = if (panno.isNullOrBlank()) "—" else Masking.pan(panno),
        // The server stores the address with CR/LF; show it on one line.
        address = addsdt?.replace(Regex("\\s*[\\r\\n]+\\s*"), ", ")?.trim(' ', ',').orEmpty(),
        landmark = landmark?.trim().orEmpty(),
        city = city?.trim().orEmpty(),
        pincode = pincode?.trim().orEmpty(),
    )
}

internal fun CommissionDto.toCommission() = Commission(
    banking = bankingServices.mapNotNull { g ->
        val rows = g.slabs.filter { it.status != 0 }.mapNotNull { s ->
            val v = s.displayAmount?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            CommissionRow(s.rangeText?.trim()?.ifEmpty { null } ?: "All amounts", v)
        }
        val title = g.title?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        if (rows.isEmpty()) null else CommissionSection(title, rows, isCharge = g.serviceType.equals("CHARGES", true))
    },
    prepaid = prepaidServices.mapNotNull { g ->
        val rows = g.operators.filter { it.status != 0 }.mapNotNull { o ->
            CommissionRow(o.operatorName?.trim()?.ifEmpty { null } ?: return@mapNotNull null, o.displayAmount?.trim()?.ifEmpty { null } ?: return@mapNotNull null)
        }
        val title = g.typeName?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        if (rows.isEmpty()) null else CommissionSection(title, rows)
    },
)

internal fun ServiceStatusDto.toActive(): ActiveService? {
    val name = services_name?.trim()?.ifEmpty { null } ?: return null
    val p = status
    val on = p?.booleanOrNull ?: when (p?.contentOrNull?.trim()?.lowercase()) { "1", "true", "active", "yes" -> true; null -> true; else -> false }
    return ActiveService(name, service_desc?.trim()?.ifEmpty { null }, on)
}
