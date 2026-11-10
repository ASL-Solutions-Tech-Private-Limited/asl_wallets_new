package com.asl.wallet.feature.dmt.data

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.feature.dmt.domain.Charges
import com.asl.wallet.feature.dmt.domain.Customer
import com.asl.wallet.feature.dmt.domain.CustomerLookup
import com.asl.wallet.feature.dmt.domain.DmtBank
import com.asl.wallet.feature.dmt.domain.DmtOutcome
import com.asl.wallet.feature.dmt.domain.DmtReceipt
import com.asl.wallet.feature.dmt.domain.DmtRules
import com.asl.wallet.feature.dmt.domain.DmtStatus
import com.asl.wallet.feature.dmt.domain.Gender
import com.asl.wallet.feature.dmt.domain.OtpPurpose
import com.asl.wallet.feature.dmt.domain.Recipient
import com.asl.wallet.feature.dmt.domain.RecipientForm
import com.asl.wallet.feature.dmt.domain.TransferMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** One confirmed transfer. [key] is made once per confirmed transfer and never reused for another. */
data class TransferInput(
    val customer: Customer,
    val recipient: Recipient,
    val mode: TransferMode,
    val amount: Int,
    val charge: BigDecimal?,
    val otp: String,
    val key: IdempotencyKey,
)

interface DmtRepository {
    suspend fun lookup(mobile: String): NetworkResult<CustomerLookup>
    suspend fun register(mobile: String, name: String, gender: Gender): NetworkResult<Unit>
    suspend fun sendOtp(phone: String, purpose: OtpPurpose, account: String? = null): NetworkResult<Unit>
    suspend fun verifyOtp(phone: String, purpose: OtpPurpose, otp: String, account: String? = null): NetworkResult<Unit>
    suspend fun recipients(mobile: String): NetworkResult<List<Recipient>>
    suspend fun banks(): NetworkResult<List<DmtBank>>
    /** Paid (₹ 3). Returns the account holder's name from the bank. */
    suspend fun checkName(form: RecipientForm): NetworkResult<String>
    /** Saves the recipient; the server then sends an OTP to the recipient's mobile to confirm it. */
    suspend fun addRecipient(customerMobile: String, form: RecipientForm): NetworkResult<Unit>
    /** null when the server has no charges API yet. */
    suspend fun charges(amount: Int, mode: TransferMode): NetworkResult<Charges?>
    suspend fun sendTransferOtp(): NetworkResult<Unit>
    /** Never throws and never retries: a timeout or 5xx is [DmtOutcome.Pending]. */
    suspend fun transfer(input: TransferInput): DmtOutcome
    suspend fun balance(): NetworkResult<BigDecimal>
}

@Singleton
class DmtRepositoryImpl @Inject constructor(private val api: DmtApi, private val json: Json) : DmtRepository {
    @Volatile private var bankCache: List<DmtBank>? = null

    override suspend fun lookup(mobile: String): NetworkResult<CustomerLookup> =
        when (val r = safeApiCall(json, allowEmptyData = true) { api.checkRemitter(RemitterLookup(mobile, OtpPurpose.Customer.api)) }) {
            is NetworkResult.Failure -> r
            is NetworkResult.Success -> readLookup(mobile, r.data as? JsonElement ?: JsonObject(emptyMap()))?.let { NetworkResult.Success(it, r.message) }
                ?: NetworkResult.Failure.Api(422, "LOOKUP", innerMessage(r.data) ?: r.message ?: "Couldn't check this customer.")
        }

    override suspend fun register(mobile: String, name: String, gender: Gender) =
        checked { api.addRemitter(AddRemitterRequest(name.trim(), mobile, gender.api)) }

    override suspend fun sendOtp(phone: String, purpose: OtpPurpose, account: String?) =
        checked { api.generateOtp(OtpRequest(phone, purpose.api, beneficiaryAccno = account)) }

    override suspend fun verifyOtp(phone: String, purpose: OtpPurpose, otp: String, account: String?) =
        checked { api.verifyOtp(OtpRequest(phone, purpose.api, otp, account)) }

    /**
     * UAT answers HTTP 200 + `status: true` with the real result one level down, e.g.
     * `{"data":{"status":false,"message":"Invalid OTP."}}` (5 Nov 2026). That inner `false` is a failure.
     */
    private suspend fun checked(call: suspend () -> retrofit2.Response<com.asl.wallet.core.network.model.ApiEnvelope<JsonElement>>): NetworkResult<Unit> =
        when (val r = safeApiCall(json, allowEmptyData = true) { call() }) {
            is NetworkResult.Failure -> r
            is NetworkResult.Success -> innerFailure(r.data) ?: NetworkResult.Success(Unit, innerMessage(r.data) ?: r.message)
        }

    override suspend fun recipients(mobile: String) = safeApiCall(json) { api.recipients(PhoneRequest(mobile)) }.map { readRecipients(it) }

    override suspend fun banks(): NetworkResult<List<DmtBank>> {
        bankCache?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.banks() }.map { list ->
            list.filter { it.status?.contentOrNull?.trim() != "0" }.mapNotNull { b ->
                val id = b.id?.contentOrNull?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                val name = b.bankName?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                DmtBank(id, name, b.bankCode?.trim()?.uppercase()?.ifEmpty { null })
            }.sortedBy { it.name.uppercase() }
        }.also { if (it is NetworkResult.Success && it.data.isNotEmpty()) bankCache = it.data }
    }

    override suspend fun checkName(form: RecipientForm): NetworkResult<String> {
        val r = safeApiCall(json) { api.checkName(NameCheckRequest(form.account, form.ifsc.uppercase(), form.mobile, form.bank!!.name, isExist = 0)) }
        return when (r) {
            is NetworkResult.Success -> innerFailure(r.data) ?: readName(r.data)?.let { NetworkResult.Success(DmtRules.cleanName(it), r.message) }
                ?: NetworkResult.Failure.Api(422, "NAME_EMPTY", r.message?.let(::safeServerMessage) ?: "The bank didn't return a name.")
            is NetworkResult.Failure -> r
        }
    }

    override suspend fun addRecipient(customerMobile: String, form: RecipientForm) = checked {
        api.addRecipient(
            AddRecipientRequest(
                phone = customerMobile, beneficiaryName = DmtRules.cleanName(form.name).uppercase(), beneficiaryAccNo = form.account,
                bankIfscCod = form.ifsc.uppercase(), beneficiaryBankName = form.bank!!.name, beneficiaryMobileNumber = form.mobile, trnasferMode = TransferMode.Imps.api,
                // 1 = the bank name check was done for this account (even if the name was edited after), else 0.
                isVerified = if (form.nameChecked) "1" else "0",
            ),
        )
    }

    override suspend fun charges(amount: Int, mode: TransferMode): NetworkResult<Charges?> =
        when (val r = safeApiCall(json) { api.charges(ChargesRequest(amount, mode.api)) }) {
            is NetworkResult.Success -> NetworkResult.Success(readCharges(r.data, amount), r.message)
            // The charges API isn't there yet (404): no charge to show, the flow continues.
            is NetworkResult.Failure.Api -> if (r.httpCode == 404) NetworkResult.Success(null, null) else r
            is NetworkResult.Failure -> r
        }

    override suspend fun sendTransferOtp() = checked { api.sendTransferOtp() }

    override suspend fun transfer(input: TransferInput): DmtOutcome {
        val r = safeApiCall(json) {
            api.transfer(input.key.value, TransferRequest(input.customer.mobile, input.recipient.id, input.mode.api, input.amount.toString(), input.otp))
        }
        fun receipt(refs: List<String> = emptyList(), txn: String? = null, time: String = "") = DmtReceipt(
            input.customer.name, input.customer.mobile, input.recipient.name, input.recipient.bankName, input.recipient.last4,
            input.mode.api, BigDecimal(input.amount), input.charge, refs, txn, time,
        )
        return when (r) {
            is NetworkResult.Success -> {
                innerFailure(r.data)?.let { f ->
                    return if (f.message.contains("otp", ignoreCase = true)) DmtOutcome.WrongOtp(safeServerMessage(f.message)) else DmtOutcome.Failed(receipt(), safeServerMessage(f.message))
                }
                val (status, refs, txn) = readTransfer(r.data)
                val rec = receipt(refs, txn)
                when (DmtStatus.of(status)) {
                    1 -> DmtOutcome.Sent(rec)
                    -1 -> DmtOutcome.Failed(rec, (r.data as? JsonObject)?.str("message", "msg", "remark")?.let(::safeServerMessage) ?: r.message?.let(::safeServerMessage))
                    else -> DmtOutcome.Pending(rec)
                }
            }
            NetworkResult.Failure.Timeout, NetworkResult.Failure.NoInternet -> DmtOutcome.Pending(receipt())
            is NetworkResult.Failure.Api -> when {
                r.httpCode >= 500 -> DmtOutcome.Pending(receipt())
                r.message.contains("otp", ignoreCase = true) -> DmtOutcome.WrongOtp(safeServerMessage(r.message))
                // A 4xx is a refusal before any money moved, not a bank decline.
                else -> DmtOutcome.Refused(safeServerMessage(r.message))
            }
            is NetworkResult.Failure -> DmtOutcome.Failed(receipt(), null)
        }
    }

    override suspend fun balance(): NetworkResult<BigDecimal> = when (val r = safeApiCall(json) { api.accountInfo() }) {
        is NetworkResult.Success -> (r.data.availableBalanceAmt.amount() ?: r.data.available_balance_amt.amount())
            ?.let { NetworkResult.Success(it, null) } ?: NetworkResult.Failure.Unexpected("Balance missing")
        is NetworkResult.Failure -> r
    }
}

// ---------------------------------------------------------------- mapping (internal for tests)

internal fun JsonPrimitive?.amount(): BigDecimal? =
    this?.contentOrNull?.replace(",", "")?.replace("₹", "")?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

internal fun JsonObject.str(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }?.takeIf { it != "-" } }

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

private fun JsonPrimitive?.truthy(): Boolean? {
    if (this == null) return null
    booleanOrNull?.let { return it }
    return when (contentOrNull?.trim()?.lowercase()) { "1", "true", "y", "yes", "verified" -> true; "0", "false", "n", "no" -> false; else -> null }
}

private fun JsonPrimitive.flagOrNull(): Boolean? = booleanOrNull ?: when (contentOrNull?.trim()?.lowercase()) {
    "true", "1", "success", "ok" -> true
    "false", "0", "fail", "failed", "failure", "error" -> false
    else -> null
}

/** The inner `status: false` of a 200 answer, as an API failure with the server's message. */
internal fun innerFailure(data: Any?): NetworkResult.Failure.Api? {
    val o = data as? JsonObject ?: return null
    if ((o["status"] as? JsonPrimitive)?.flagOrNull() != false) return null
    return NetworkResult.Failure.Api(422, "INNER_STATUS", o.str("message", "msg") ?: "The request was not completed.")
}

internal fun innerMessage(data: Any?): String? = (data as? JsonObject)?.str("message", "msg")

/**
 * The customer lookup. UAT (5 Nov 2026) answers with a message and, when found, the customer in `data.data`:
 * "User not exist" (status false) → not registered; "Verify your OTP" (+ otpReference) → OTP sent;
 * "Remitter verified successfully" + {cust_name, totalLimit, consumedLimit, rem_limit, isverify} → found.
 * The old app's `statusCheck` "0" / "1" / "2" is read too. null = an answer we can't place (shown as an error).
 */
internal fun readLookup(mobile: String, e: JsonElement): CustomerLookup? {
    val top = e.obj() ?: return null
    val inner = top["data"].obj()
    when (top.str("statusCheck", "status_check") ?: inner?.str("statusCheck", "status_check")) {
        "0" -> return CustomerLookup.NotRegistered
        "1" -> return CustomerLookup.NeedsOtp
    }
    val cust = inner?.takeIf { it.str("cust_name") != null } ?: top.takeIf { it.str("cust_name") != null }
    if (cust != null) {
        if ((cust["isverify"] as? JsonPrimitive)?.flagOrNull() == false) return CustomerLookup.NeedsOtp
        val total = (cust["totalLimit"] as? JsonPrimitive).amount()
        val left = (cust["rem_limit"] as? JsonPrimitive).amount()
        val used = (cust["consumedLimit"] as? JsonPrimitive).amount() ?: if (total != null && left != null) total - left else null
        return CustomerLookup.Found(Customer(cust.str("cust_mob", "phone")?.filter(Char::isDigit)?.takeIf { it.length >= 10 }?.takeLast(10) ?: mobile,
            cust.str("cust_name", "name") ?: "", total, used))
    }
    val message = top.str("message", "msg").orEmpty().lowercase()
    val failed = (top["status"] as? JsonPrimitive)?.flagOrNull() == false
    return when {
        failed && listOf("not exist", "not found", "not registered", "no record").any { it in message } -> CustomerLookup.NotRegistered
        !failed && (top["otpReference"] != null || "otp" in message) -> CustomerLookup.NeedsOtp
        else -> null
    }
}

internal fun readRecipients(e: JsonElement): List<Recipient> {
    val arr = (e as? JsonArray) ?: (e.obj()?.get("data") as? JsonArray) ?: (e.obj()?.get("beneficiaries") as? JsonArray) ?: return emptyList()
    return arr.mapNotNull { item ->
        val o = item.obj() ?: return@mapNotNull null
        val id = o.str("id", "beneid", "beneficiaryId") ?: return@mapNotNull null
        val acc = o.str("bene_accno", "accountNumber", "beneficiaryAccNo")?.filter(Char::isLetterOrDigit) ?: return@mapNotNull null
        Recipient(
            id = id,
            name = o.str("bene_name", "beneficiaryName", "name") ?: "—",
            bankName = o.str("bene_bank", "bankName", "beneficiaryBankName") ?: "Bank",
            accountNumber = acc,
            ifsc = o.str("bene_ifsc", "ifscCode", "ifsc")?.uppercase().orEmpty(),
            mobile = o.str("bene_mobno", "beneficiaryMobileNumber", "mobile"),
            verified = (o["isBankVerified"] as? JsonPrimitive).truthy() ?: (o["isverify"] as? JsonPrimitive).truthy() ?: (o["verify_ch"] as? JsonPrimitive).truthy() ?: false,
        )
    }
}

internal fun readName(e: JsonElement): String? {
    val o = e.obj() ?: return (e as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null }
    return o.str("name", "beneName", "beneficiaryName", "accountHolderName", "nameAtBank") ?: o["data"].obj()?.str("name", "beneName", "accountHolderName")
}

internal fun readCharges(e: JsonElement, amount: Int): Charges? {
    val o = e.obj() ?: return null
    val charge = (o["charge"] as? JsonPrimitive ?: o["charges"] as? JsonPrimitive ?: o["serviceCharge"] as? JsonPrimitive).amount() ?: return null
    val total = (o["total"] as? JsonPrimitive ?: o["totalDebited"] as? JsonPrimitive).amount() ?: (BigDecimal(amount) + charge)
    return Charges(charge, total)
}

/** Status, every bank ref (the backend may split one amount into several transfers) and the ASL txn id. */
internal fun readTransfer(e: JsonElement): Triple<String?, List<String>, String?> {
    val outer = e.obj() ?: return Triple(null, emptyList(), null)
    // UAT nests results one level down (data.data); merge both, inner values win.
    val o = outer["data"].obj()?.let { JsonObject(outer + it) } ?: outer
    val parts = listOf("transactions", "splits", "data").firstNotNullOfOrNull { o[it] as? JsonArray }?.mapNotNull { it.obj() }.orEmpty()
    val refs = (listOf(o) + parts).mapNotNull { it.str("providerTransId", "utr", "bankref", "bankref_num", "bankRef", "rrn") }.distinct()
    val partStatuses = parts.mapNotNull { it.str("transactionstatus", "status", "tranStatus") }
    val status = o.str("transactionstatus", "transactionStatus", "tranStatus", "txnStatus")
        ?: o.str("status")?.takeIf { (o["status"] as? JsonPrimitive)?.booleanOrNull == null }
        ?: when {
            partStatuses.isEmpty() -> null
            partStatuses.all { DmtStatus.of(it) == 1 } -> "SUCCESS"
            partStatuses.all { DmtStatus.of(it) == -1 } -> "FAILED"
            else -> "PENDING"
        }
    return Triple(status, refs, o.str("systemTransId", "servid", "servId", "transactionId", "orderId"))
}
