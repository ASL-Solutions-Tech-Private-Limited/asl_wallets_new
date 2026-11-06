package com.asl.wallet.feature.aeps.data

import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeApiCallUnit
import com.asl.wallet.feature.aeps.domain.AepsConfig
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.AepsReceipt
import com.asl.wallet.feature.aeps.domain.AgentPrefill
import com.asl.wallet.feature.aeps.domain.AgentStatus
import com.asl.wallet.feature.aeps.domain.CaptureType
import com.asl.wallet.feature.aeps.domain.CustomerBank
import com.asl.wallet.feature.aeps.domain.GeoPoint
import com.asl.wallet.feature.aeps.domain.OnboardingForm
import com.asl.wallet.feature.aeps.domain.Pid
import com.asl.wallet.feature.aeps.domain.ProviderInfo
import com.asl.wallet.feature.aeps.domain.RdDevice
import com.asl.wallet.feature.aeps.domain.StatementLine
import com.asl.wallet.feature.aeps.domain.TxnOutcome
import com.asl.wallet.feature.aeps.domain.TxnType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

data class OtpRef(val referenceId: String, val hash: String, val merchantLoginId: String?)
data class ValidatedOtp(val referenceId: String, val hash: String, val wadh: String?, val outletId: String?)

/** Photos for onboarding, already compressed JPEG bytes. */
class OnboardingPhotos(val aadhaar: ByteArray, val pan: ByteArray, val shop: ByteArray)

interface AepsRepository {
    suspend fun config(): NetworkResult<AepsConfig>
    suspend fun identity(): NetworkResult<AgentIdentity>
    /** Read-only check. Callers that start a flow put the result in [AepsSession] themselves. */
    suspend fun status(provider: AepsProvider): NetworkResult<AgentStatus>
    suspend fun onboard(provider: AepsProvider, form: OnboardingForm, photos: OnboardingPhotos, at: GeoPoint): NetworkResult<OtpRef>
    suspend fun resendOnboardingOtp(provider: AepsProvider, form: OnboardingForm, merchantLoginId: String, at: GeoPoint): NetworkResult<OtpRef>
    suspend fun validateOnboardingOtp(provider: AepsProvider, otp: String, ref: OtpRef): NetworkResult<ValidatedOtp>
    suspend fun onboardingBiometric(provider: AepsProvider, aadhaar: String, merchantLoginId: String?, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit>
    suspend fun bankSendOtp(provider: AepsProvider, at: GeoPoint): NetworkResult<OtpRef>
    suspend fun bankValidateOtp(provider: AepsProvider, otp: String, ref: OtpRef): NetworkResult<ValidatedOtp>
    suspend fun bankBiometric(provider: AepsProvider, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit>
    suspend fun biometricKyc(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit>
    suspend fun dailyLogin(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit>
    suspend fun customerOtp(provider: AepsProvider, type: TxnType, aadhaar: String, mobile: String, bank: CustomerBank, amount: BigDecimal, at: GeoPoint): NetworkResult<String>
    suspend fun transact(request: TxnInput): TxnOutcome
}

data class TxnInput(
    val provider: AepsProvider,
    val type: TxnType,
    val aadhaar: String,
    val mobile: String,
    val bank: CustomerBank,
    val amount: BigDecimal?,
    val capture: CaptureType,
    val pid: String,
    val at: GeoPoint,
    val otpTransactionId: String? = null,
    /** One key per attempt the retailer starts; a resend of the same attempt must reuse it. */
    val idempotencyKey: IdempotencyKey = IdempotencyKey.new(),
)

@Singleton
class AepsRepositoryImpl @Inject constructor(
    private val api: AepsApi,
    private val identityApi: AgentIdentityApi,
    private val session: AepsSession,
    private val json: Json,
) : AepsRepository {

    override suspend fun config(): NetworkResult<AepsConfig> {
        session.config?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.init() }.map { it.toConfig() }.also { r -> if (r is NetworkResult.Success) session.config = r.data }
    }

    override suspend fun identity(): NetworkResult<AgentIdentity> {
        session.identity?.let { return NetworkResult.Success(it, null) }
        return when (val r = safeApiCall(json) { identityApi.me() }) {
            is NetworkResult.Success -> {
                val aadhaar = r.data.aadharno?.filter(Char::isDigit).orEmpty()
                val mobile = r.data.phone?.filter(Char::isDigit)?.takeLast(10).orEmpty()
                if (aadhaar.length != 12 || mobile.length != 10) NetworkResult.Failure.Api(422, "AGENT_ID", MISSING_IDENTITY)
                else AgentIdentity(
                    aadhaar, mobile, r.data.email?.trim().orEmpty(), r.data.panno?.trim()?.uppercase().orEmpty(),
                    firstName = r.data.fname?.trim().orEmpty(), lastName = r.data.lname?.trim().orEmpty(),
                    address = r.data.addsdt?.replace(Regex("\\s*[\\r\\n]+\\s*"), ", ")?.trim(' ', ',').orEmpty(),
                    city = r.data.city?.trim().orEmpty(), pincode = r.data.pincode?.trim().orEmpty(), shopName = r.data.company_name?.trim().orEmpty(),
                )
                    .also { session.identity = it }.let { NetworkResult.Success(it, null) }
            }
            is NetworkResult.Failure -> r
        }
    }

    override suspend fun status(provider: AepsProvider): NetworkResult<AgentStatus> {
        val id = when (val r = identity()) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        return safeApiCall(json) { api.checkAgentStatus(StatusRequest(provider.id, id.aadhaar, id.mobile)) }
            .map { it.toStatus(json) }
    }

    override suspend fun onboard(provider: AepsProvider, form: OnboardingForm, photos: OnboardingPhotos, at: GeoPoint): NetworkResult<OtpRef> {
        val text = "text/plain".toMediaType()
        val fields = form.fields(provider, at).mapValues { it.value.toRequestBody(text) }
        fun part(name: String, bytes: ByteArray) = MultipartBody.Part.createFormData(name, "$name.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return safeApiCall(json) {
            api.onboarding(fields, part("aadharImage", photos.aadhaar), part("panImage", photos.pan), part("shopImage", photos.shop))
        }.map { it.toRef() }
    }

    override suspend fun resendOnboardingOtp(provider: AepsProvider, form: OnboardingForm, merchantLoginId: String, at: GeoPoint) =
        safeApiCall(json) {
            api.sendOtp(SendOtpRequest(form.phone.trim(), form.aadhaar.filter(Char::isDigit), form.pan.trim().uppercase(), at.lat, at.lon, merchantLoginId, provider.id, form.email.trim().ifEmpty { null }))
        }.map { it.toRef(merchantLoginId) }

    override suspend fun validateOnboardingOtp(provider: AepsProvider, otp: String, ref: OtpRef) =
        safeApiCall(json) { api.validateOtp(ValidateOtpRequest(otp, ref.referenceId, ref.hash, provider.id)) }.map { it.toValidated(ref) }

    override suspend fun onboardingBiometric(provider: AepsProvider, aadhaar: String, merchantLoginId: String?, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint) =
        safeApiCallUnit(json) {
            api.biometricKyc(BiometricRequest(aadhaar.filter(Char::isDigit), merchantLoginId ?: v.outletId, v.referenceId, v.hash, provider.id, capture.name, pid, at.lat, at.lon))
        }

    override suspend fun bankSendOtp(provider: AepsProvider, at: GeoPoint): NetworkResult<OtpRef> {
        val id = when (val r = identity()) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        val outlet = session.status?.outletId.orEmpty()
        return safeApiCall(json) {
            api.bankSendOtp(SendOtpRequest(id.mobile, id.aadhaar, id.pan, at.lat, at.lon, outlet, provider.id, id.email.ifEmpty { null }))
        }.map { it.toRef(outlet) }
    }

    override suspend fun bankValidateOtp(provider: AepsProvider, otp: String, ref: OtpRef) =
        safeApiCall(json) { api.bankValidateOtp(ValidateOtpRequest(otp, ref.referenceId, ref.hash, provider.id)) }.map { it.toValidated(ref) }

    override suspend fun bankBiometric(provider: AepsProvider, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit> {
        val id = when (val r = identity()) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        return safeApiCallUnit(json) {
            api.bankBiometricKyc(BiometricRequest(id.aadhaar, session.status?.outletId, v.referenceId, v.hash, provider.id, capture.name, pid, at.lat, at.lon))
        }
    }

    override suspend fun biometricKyc(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit> {
        val id = when (val r = identity()) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        val st = session.status
        return safeApiCallUnit(json) {
            api.biometricKyc(BiometricRequest(id.aadhaar, st?.outletId, null, st?.referenceKey.orEmpty().ifEmpty { st?.outletId.orEmpty() }, provider.id, capture.name, pid, at.lat, at.lon))
        }
    }

    override suspend fun dailyLogin(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint): NetworkResult<Unit> {
        val id = when (val r = identity()) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        return safeApiCallUnit(json) {
            api.dailyLogin(DailyLoginRequest(id.aadhaar, provider.id, capture.name, pid, at.lat, at.lon, session.status?.outletId.orEmpty(), id.mobile))
        }
    }

    override suspend fun customerOtp(provider: AepsProvider, type: TxnType, aadhaar: String, mobile: String, bank: CustomerBank, amount: BigDecimal, at: GeoPoint): NetworkResult<String> =
        when (val r = safeApiCall(json) { api.transactionOtp(TxnOtpRequest(aadhaar, type.code, at.lat, at.lon, amount.toPlainString(), bank.iin, mobile, provider.id)) }) {
            is NetworkResult.Success -> {
                val id = (r.data.fpTransactionId ?: r.data.otpTransactionId)?.trim().orEmpty()
                if (id.isEmpty()) NetworkResult.Failure.Api(502, "NO_OTP_REF", OTP_REF_MISSING) else NetworkResult.Success(id, r.message)
            }
            // `data: {}` parses to an empty DTO; a null data means the same thing here.
            is NetworkResult.Failure.Unexpected -> NetworkResult.Failure.Api(502, "NO_OTP_REF", OTP_REF_MISSING)
            is NetworkResult.Failure -> r
        }

    override suspend fun transact(request: TxnInput): TxnOutcome {
        val st = session.status
        val body = TxnRequest(
            aadhaarNo = request.aadhaar, txnType = request.type.code, latitude = request.at.lat, longitude = request.at.lon,
            captureType = request.capture.name, outletId = st?.outletId.orEmpty(),
            amount = request.amount?.toPlainString() ?: "0", bankiin = request.bank.iin, mobile = request.mobile,
            providerId = request.provider.id, otp_transation_id = request.otpTransactionId, biometricData = request.pid,
        )
        return when (val r = safeApiCall(json) { api.transaction(request.idempotencyKey.value, body) }) {
            is NetworkResult.Success -> TxnOutcome.Success(r.data.toReceipt(request, r.message))
            is NetworkResult.Failure.Timeout -> TxnOutcome.Pending
            is NetworkResult.Failure.Api -> when {
                isProviderUnavailable(r.code, r.message) -> TxnOutcome.ProviderUnavailable
                // A server error after the request was sent: the outcome is unknown, so treat it as pending.
                r.httpCode >= 500 -> TxnOutcome.Pending
                else -> TxnOutcome.Failed(com.asl.wallet.core.network.safeServerMessage(r.message))
            }
            is NetworkResult.Failure -> TxnOutcome.Failed(null)
        }
    }

    companion object {
        const val MISSING_IDENTITY = "Your Aadhaar or mobile number is missing from your ASL profile. Please contact ASL support."
        const val OTP_REF_MISSING = "The bank didn't return an OTP reference. Please try an amount up to ₹5,000 or try again later."
        fun isProviderUnavailable(code: String?, message: String?) =
            code.equals("PROV001", true) || message?.contains("PROV001", true) == true || message?.contains("not implemented", true) == true
    }
}

// ---------------------------------------------------------------- mapping (internal for tests)

private fun JsonPrimitive?.text(): String? = this?.contentOrNull?.trim()?.ifEmpty { null }
private fun JsonPrimitive?.on(): Boolean = when (this?.contentOrNull?.trim()?.lowercase()) { "0", "false", "no" -> false; else -> true }

internal fun InitDto.toConfig() = AepsConfig(
    providers = providerInfo.mapNotNull { p -> AepsProvider.of(p.providerId?.intOrNull ?: p.providerId.text()?.toIntOrNull())?.let { ProviderInfo(it, p.isActive.on()) } }
        .distinctBy { it.provider },
    // The UAT list has padded and blank rows: trim, drop empties and duplicates, sort by name.
    banks = bankList.mapNotNull { b ->
        val name = b.bankName?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        val iin = b.bankIino?.trim().orEmpty()
        if (name.isEmpty() || !iin.all(Char::isDigit) || iin.length !in 6..7) null else CustomerBank(iin, name)
    }.distinctBy { it.iin }.sortedBy { it.name.lowercase() },
    devices = deviceInfo.mapNotNull { d ->
        val name = d.name?.trim().orEmpty()
        val pkg = Pid.PACKAGES[name.lowercase()] ?: return@mapNotNull null
        if (!d.isActive.on()) null else RdDevice(d.deviceId?.trim().orEmpty(), name.replace("FaceAuth", "Face authentication"), pkg, isFace = pkg == "in.gov.uidai.facerd")
    },
    quickAmounts = allowedAmount.mapNotNull { it.contentOrNull?.toBigDecimalOrNull()?.toLong() }.filter { it > 0 }.distinct().sorted(),
)

internal fun StatusDto.toStatus(json: Json): AgentStatus {
    val o = bcAgentData as? JsonObject
    fun f(k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return AgentStatus(
        ekycDone = isEkycDone, bankEkycDone = isBankEkycDone, bioKycDone = isBioKyc, dailyLoginDone = isTwoFA,
        outletId = outletid?.trim().orEmpty(), wadh = pidOptionWadh?.trim()?.ifEmpty { null }, referenceKey = referenceKey?.trim()?.ifEmpty { null },
        // Full Aadhaar/PAN/account in bcAgentData are deliberately not copied except what onboarding must prefill.
        prefill = AgentPrefill(
            firstName = f("fname"), lastName = f("lname"), email = f("emailid"), phone = f("mobile"),
            address = f("address"), city = f("city"), district = f("dist"), pincode = f("pincode"),
            bankName = f("bank_name"), ifsc = f("ifsccode"), accountNo = f("accountno"), holderName = f("holdername"),
            shopName = f("shopname"), shopPan = f("shop_pan"), shopAddress = f("shop_address"), shopCity = f("shop_city"),
            shopDistrict = f("shop_dist"), shopState = f("shop_state"), shopPincode = f("shop_pincode"),
        ),
    )
}

internal fun OtpRefDto.toRef(fallbackLoginId: String? = null) = OtpRef(otpReferenceId.text().orEmpty(), hash?.trim().orEmpty(), merchantLoginId?.trim()?.ifEmpty { null } ?: fallbackLoginId)

internal fun ValidatedOtpDto.toValidated(ref: OtpRef) = ValidatedOtp(
    referenceId = primaryKeyId.text() ?: ref.referenceId,
    hash = encodeFPTxnId?.trim()?.ifEmpty { null } ?: referenceKey?.trim()?.ifEmpty { null } ?: ref.hash,
    wadh = pidOptionWadh?.trim()?.ifEmpty { null },
    outletId = outletId?.trim()?.ifEmpty { null },
)

internal fun TxnDto.toReceipt(req: TxnInput, message: String?) = AepsReceipt(
    type = req.type,
    provider = req.provider,
    amount = transactionAmount?.trim()?.ifEmpty { null }?.toBigDecimalOrNull() ?: req.amount,
    bankBalance = bankAccountBalance?.trim()?.ifEmpty { null },
    bankName = bank?.trim()?.ifEmpty { null } ?: req.bank.name,
    bankRef = txnReferenceId?.trim().orEmpty(),
    customerAadhaarMasked = Masking.aadhaar(req.aadhaar),
    customerMobileMasked = Masking.mobile(req.mobile),
    time = timestamp?.trim().orEmpty(),
    statement = miniStatementData.mapNotNull { it.toLine() },
    message = message,
)

/** FingPay mini statement row: {date, txnType "Cr"/"Dr", amount, narration}. Other shapes are skipped. */
internal fun JsonElement.toLine(): StatementLine? {
    val o = this as? JsonObject ?: return null
    fun f(vararg k: String) = k.firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } }
    val amount = f("amount", "txnAmount") ?: return null
    val type = f("txnType", "type", "drcr").orEmpty()
    return StatementLine(
        date = f("date", "txnDate").orEmpty(),
        narration = maskDigits(f("narration", "description", "remarks").orEmpty()),
        isCredit = type.startsWith("C", ignoreCase = true),
        amount = amount,
    )
}

/** Account or card numbers inside narrations show only the last 4 digits. */
internal fun maskDigits(text: String): String = Regex("\\d{9,}").replace(text) { m -> Masking.account(m.value) }
