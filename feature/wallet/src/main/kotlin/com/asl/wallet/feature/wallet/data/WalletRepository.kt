package com.asl.wallet.feature.wallet.data

import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.model.ApiEnvelope
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeApiCallUnit
import com.asl.wallet.feature.wallet.domain.AccountOwner
import com.asl.wallet.feature.wallet.domain.AddBankForm
import com.asl.wallet.feature.wallet.domain.AddMoneyInfo
import com.asl.wallet.feature.wallet.domain.AddMoneyResult
import com.asl.wallet.feature.wallet.domain.AddMoneyStatus
import com.asl.wallet.feature.wallet.domain.BankAccount
import com.asl.wallet.feature.wallet.domain.BankOption
import com.asl.wallet.feature.wallet.domain.CompanyBank
import com.asl.wallet.feature.wallet.domain.FundMode
import com.asl.wallet.feature.wallet.domain.FundRequestForm
import com.asl.wallet.feature.wallet.domain.FundRequestItem
import com.asl.wallet.feature.wallet.domain.FundRequestPage
import com.asl.wallet.feature.wallet.domain.FundRequestSetup
import com.asl.wallet.feature.wallet.domain.FundStatus
import com.asl.wallet.feature.wallet.domain.FundSummary
import com.asl.wallet.feature.wallet.domain.Limits
import com.asl.wallet.feature.wallet.domain.PayUOrder
import com.asl.wallet.feature.wallet.payu.HashAsk
import com.asl.wallet.feature.wallet.domain.SendAccount
import com.asl.wallet.feature.wallet.domain.SendMoneySetup
import com.asl.wallet.feature.wallet.domain.SendOutcome
import com.asl.wallet.feature.wallet.domain.SendReceipt
import com.asl.wallet.feature.wallet.domain.WalletEntry
import com.asl.wallet.feature.wallet.domain.WalletOverview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** What creating an Add money order gave back. */
sealed interface OrderResult {
    data class Ready(val order: PayUOrder) : OrderResult
    /** The server made an order but sent no PayU SDK fields (backend change still in progress). Nothing was charged. */
    data object NotSupportedYet : OrderResult
}

interface WalletRepository {
    suspend fun overview(): NetworkResult<WalletOverview>
    suspend fun recent(): NetworkResult<List<WalletEntry>>

    suspend fun addMoneyInfo(): NetworkResult<AddMoneyInfo>
    /** [key] is one per Pay tap; the same key is reused if the same order is retried. */
    suspend fun createOrder(amount: BigDecimal, key: IdempotencyKey): NetworkResult<OrderResult>
    suspend fun addMoneyStatus(txnId: String, amount: BigDecimal?): NetworkResult<AddMoneyResult>
    suspend fun payuHash(ask: HashAsk): String?

    suspend fun fundSetup(): NetworkResult<FundRequestSetup>
    suspend fun submitFund(form: FundRequestForm, adminId: String, editId: Long? = null): NetworkResult<String?>
    suspend fun deleteFund(id: Long): NetworkResult<Unit>
    suspend fun fundRequests(page: Int, status: FundStatus?): NetworkResult<FundRequestPage>

    suspend fun sendSetup(): NetworkResult<SendMoneySetup>
    /** Success carries where the OTP went (masked mobile), when the server says. */
    suspend fun sendOtp(account: SendAccount, amount: BigDecimal, mode: String): NetworkResult<String?>
    /** Never throws and never retries: a timeout is [SendOutcome.Pending]. [at] is the shop's real location. */
    suspend fun sendMoney(account: SendAccount, amount: BigDecimal, mode: String, otp: String, key: IdempotencyKey, at: ShopLocation): SendOutcome

    suspend fun bankAccounts(): NetworkResult<List<BankAccount>>
    suspend fun bankOptions(): NetworkResult<List<BankOption>>
    suspend fun addBank(form: AddBankForm, pan: ByteArray, passbook: ByteArray): NetworkResult<BankAccount>
    suspend fun deleteBank(account: BankAccount): NetworkResult<Unit>
}

@Singleton
class WalletRepositoryImpl @Inject constructor(
    private val api: WalletApi,
    private val json: Json,
    /** A Provider: read lazily (the session owner depends on repositories). */
    private val currentUser: javax.inject.Provider<com.asl.wallet.core.common.session.CurrentUser>,
) : WalletRepository {

    @Volatile private var options: List<BankOption>? = null

    override suspend fun overview() = safeApiCall(json) { api.accountInfo() }.let { r ->
        when (r) {
            is NetworkResult.Success -> {
                val d = r.data
                val balance = (d.availableBalanceAmt.amount() ?: d.available_balance_amt.amount())
                    ?: return NetworkResult.Failure.Unexpected("Balance missing")
                NetworkResult.Success(
                    WalletOverview(
                        balance = balance,
                        eTopup = d.eTopupPurchase.amount() ?: d.e_topup_purchase.amount() ?: BigDecimal.ZERO,
                        sales = d.salesAmt.amount() ?: d.sales_amt.amount() ?: BigDecimal.ZERO,
                        commission = d.commissionReceived.amount() ?: d.commission_received.amount() ?: BigDecimal.ZERO,
                    ),
                    r.message,
                )
            }
            is NetworkResult.Failure -> r
        }
    }

    override suspend fun recent() = safeApiCall(json) { api.ledger(1, RECENT) }.map { list ->
        list.mapNotNull { it.toEntry() }.take(RECENT)
    }

    // ------------------------------------------------------------ add money

    override suspend fun addMoneyInfo() = safeApiCall(json) { api.addMoneyInfo() }.map { d ->
        AddMoneyInfo(
            balance = d.wallet?.balanceAmt.amount() ?: d.wallet?.balance_amt.amount(),
            limits = d.limits.toLimits(DEFAULT_ADD_LIMITS),
            modes = d.paymentModes,
        )
    }

    override suspend fun createOrder(amount: BigDecimal, key: IdempotencyKey) =
        safeApiCall(json) { api.addMoney(key.value, PayUInitiateRequest(JsonPrimitive(amount.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }), PAYMENT_PURPOSE, isAndroid = true)) }
            .map { PayUFields.read(it, amount) }


    override suspend fun addMoneyStatus(txnId: String, amount: BigDecimal?) = safeApiCall(json) { api.addMoneyStatus(txnId) }.map { d ->
        AddMoneyStatus.of(
            // Success only once the wallet is credited: walletCredited=false keeps it Pending.
            status = d.status, complete = if (d.walletCredited == false) false else d.isPaymentComplete, txnId = d.txnid ?: d.clientTxnId ?: txnId,
            amount = d.amount.amount() ?: amount, mode = d.paymentMode ?: d.mode, payuRef = d.mihpayid ?: d.bankRefNo,
            reason = d.reason ?: d.message, balanceAfter = d.balanceAfter.amount(),
        )
    }

    override suspend fun payuHash(ask: HashAsk): String? =
        (safeApiCall(json) { api.payuHash(PayUHashRequest(ask.txnId, ask.hashName, ask.hashString, ask.postSalt, ask.hashType)) } as? NetworkResult.Success)?.data?.hash?.ifBlank { null }

    // ------------------------------------------------------------ fund request

    override suspend fun fundSetup(): NetworkResult<FundRequestSetup> {
        val info = when (val r = safeApiCall(json) { api.fundRequestInfo() }) { is NetworkResult.Success -> r.data; is NetworkResult.Failure -> return r }
        val adminId = adminOf(info.requestFromOptions)
        var banks = info.banks.mapNotNull { it.toBank() }
        if (banks.isEmpty()) {
            banks = when (val b = safeApiCall(json) { api.fundRequestBanks(adminId) }) { is NetworkResult.Success -> b.data.mapNotNull { it.toBank() }; is NetworkResult.Failure -> return b }
        }
        val modes = info.paymentModes.mapNotNull(FundMode::of).ifEmpty { FundMode.entries }
        return NetworkResult.Success(FundRequestSetup(adminId, banks, modes), null)
    }

    override suspend fun submitFund(form: FundRequestForm, adminId: String, editId: Long?): NetworkResult<String?> {
        val body = FundRequestBody(
            amount = form.amountText.trim(), reqform = adminId, bankid = form.bank!!.id, tranid = form.utr.trim().uppercase(),
            pwerby = form.mode!!.api, dipodate = form.date!!.iso(), remarks = form.remarks.trim().ifEmpty { null },
        )
        return safeApiCall(json) { if (editId == null) api.createFundRequest(body) else api.updateFundRequest(editId, body) }
            .map { refOf(it) }
    }

    override suspend fun deleteFund(id: Long) = safeApiCallUnit(json) { api.deleteFundRequest(id) }

    override suspend fun fundRequests(page: Int, status: FundStatus?): NetworkResult<FundRequestPage> {
        var envelope: ApiEnvelope<List<FundRequestRowDto>>? = null
        val call: suspend () -> Response<ApiEnvelope<List<FundRequestRowDto>>> = {
            api.fundRequests(page, PAGE, status?.let { STATUS_QUERY.getValue(it) }).also { envelope = it.body() }
        }
        val r = safeApiCall(json) { call() }.map { rows ->
            FundRequestPage(
                items = rows.mapNotNull { it.toItem() },
                summary = summaryOf(envelope?.meta),
                canLoadMore = envelope?.pagination?.canLoadMore ?: (rows.size >= PAGE),
            )
        }
        // `merchant/fund-requests` lists requests made TO this user (a distributor's downline), so it is empty for a
        // retailer (found 6 Nov). Their own requests come only as the last 10 in my-wallet/fund-request; a dated,
        // paged history API is requested. On an empty first page, show those.
        if (page == 1 && r is NetworkResult.Success && r.data.items.isEmpty()) {
            val recent = (safeApiCall(json) { api.fundRequestInfo() } as? NetworkResult.Success)?.data?.recentRequests.orEmpty()
                .mapNotNull { it.toItem() }.filter { status == null || it.status == status }
            if (recent.isNotEmpty()) return NetworkResult.Success(FundRequestPage(recent, fallbackSummary(recent), canLoadMore = false), r.message)
        }
        return r
    }

    // ------------------------------------------------------------ send money

    // Payout uses the payout collection (merchant/payout/..., user 10 Nov 2026): banks, OTP and the payout itself.

    override suspend fun sendSetup(): NetworkResult<SendMoneySetup> = when (val r = safeApiCall(json) { api.payoutBanks() }) {
        is NetworkResult.Failure -> r
        is NetworkResult.Success -> {
            val balance = (overview() as? NetworkResult.Success)?.data?.balance
            NetworkResult.Success(
                SendMoneySetup(
                    // Every active bank is listed; only verified ones can receive money (the screen shows the rest as
                    // waiting for verification, so a retailer sees all their banks).
                    accounts = readPayoutBanks(r.data).map { b ->
                        SendAccount(b.id.toString(), b.bankName, b.accountLast4, b.holder, b.ifsc, b.accountNumber, b.owner, b.verified)
                    },
                    balance = balance, limits = DEFAULT_SEND_LIMITS, modes = PAYOUT_MODES, notes = emptyList(), otpTo = null,
                ),
                r.message,
            )
        }
    }

    override suspend fun sendOtp(account: SendAccount, amount: BigDecimal, mode: String): NetworkResult<String?> =
        when (val r = safeApiCall(json, allowEmptyData = true) { api.payoutSendOtp() }) {
            is NetworkResult.Failure -> r
            // A 200 with an inner `status: false` is a refusal, not "OTP sent". UAT: data {fname, mob: "••••3077"}.
            is NetworkResult.Success -> innerRefusal(r.body())?.let { NetworkResult.Failure.Api(422, "INNER_STATUS", it) }
                ?: NetworkResult.Success((r.body() as? JsonObject)?.text("mob", "mobile", "phone")?.let(Masking::mobile), r.message)
        }

    override suspend fun sendMoney(account: SendAccount, amount: BigDecimal, mode: String, otp: String, key: IdempotencyKey, at: ShopLocation): SendOutcome {
        val body = PayoutRequest(
            accountNumber = account.accountNumber, amount = JsonPrimitive(amount), paymentMode = mode,
            latitude = "%.6f".format(java.util.Locale.ROOT, at.latitude), longitude = "%.6f".format(java.util.Locale.ROOT, at.longitude),
            otp = otp, source = "APPS",
        )
        val r = safeApiCall(json, allowEmptyData = true) { api.payout(key.value, body) }
        return when (r) {
            is NetworkResult.Success -> readPayoutResult(r.body(), account, amount, mode)
            // The request may have reached the bank: Pending, and the same key is kept if the retailer checks.
            NetworkResult.Failure.Timeout, NetworkResult.Failure.NoInternet -> SendOutcome.Pending(null)
            is NetworkResult.Failure.Api -> if (r.httpCode >= 500) SendOutcome.Pending(null) else SendOutcome.Failed(r.message)
            is NetworkResult.Failure -> SendOutcome.Failed(null)
        }
    }

    // ------------------------------------------------------------ bank accounts

    override suspend fun bankAccounts(): NetworkResult<List<BankAccount>> = safeApiCall(json) { api.payoutBanks() }.map(::readPayoutBanks)

    override suspend fun bankOptions(): NetworkResult<List<BankOption>> {
        options?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.bankOptions() }.map { list ->
            list.mapNotNull { o ->
                val id = o.id?.contentOrNull?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                val name = (o.bankName ?: o.bank_name)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                BankOption(id, name)
            }.sortedBy { it.name.uppercase() }
        }.also { if (it is NetworkResult.Success && it.data.isNotEmpty()) options = it.data }
    }

    override suspend fun addBank(form: AddBankForm, pan: ByteArray, passbook: ByteArray): NetworkResult<BankAccount> {
        // userRegID: only ever the signed-in retailer's own reg no (the server must use the login, not this: reported).
        val regno = currentUser.get().user.value?.regno ?: return NetworkResult.Failure.Unauthorized
        val text = "text/plain".toMediaType()
        val fields = mapOf(
            "userRegID" to regno.toString(),
            "bankName" to form.bank!!.name,
            "accountNumber" to form.account.trim(),
            "ifscCode" to form.ifsc.trim().uppercase(),
            "accountHolderName" to form.holder.trim().uppercase(),
            "accountType" to form.owner.api,
        ).mapValues { it.value.toRequestBody(text) }
        fun part(name: String, bytes: ByteArray) = MultipartBody.Part.createFormData(name, "$name.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        val r = safeApiCall(json, allowEmptyData = true) { api.addPayoutBank(fields, part("panImage", pan), part("passbookImage", passbook)) }
        return when (r) {
            is NetworkResult.Failure -> r
            is NetworkResult.Success -> innerRefusal(r.body())?.let { NetworkResult.Failure.Api(422, "INNER_STATUS", it) }
                ?: NetworkResult.Success(
                    BankAccount(0, form.bank.name, form.account.takeLast(4), form.holder.trim().uppercase(), form.ifsc.uppercase(), form.owner, verified = false),
                    r.message,
                )
        }
    }

    override suspend fun deleteBank(account: BankAccount): NetworkResult<Unit> {
        if (account.accountNumber.isBlank()) return NetworkResult.Failure.Unexpected("No account number")
        return when (val r = safeApiCall(json, allowEmptyData = true) { api.deletePayoutBank(PayoutAccountRequest(account.accountNumber)) }) {
            is NetworkResult.Failure -> r
            is NetworkResult.Success -> innerRefusal(r.body())?.let { NetworkResult.Failure.Api(422, "INNER_STATUS", it) } ?: NetworkResult.Success(Unit, r.message)
        }
    }

    companion object {
        const val RECENT = 3
        const val PAGE = 20
        /** Used only if the server sends no limits; the server checks again either way. */
        val DEFAULT_ADD_LIMITS = Limits(BigDecimal(500), BigDecimal(2000))
        val DEFAULT_SEND_LIMITS = Limits(BigDecimal(10), BigDecimal(50000))
        /** The payout API's paymentMode values (like DMT): IMPS first (instant). */
        val PAYOUT_MODES = listOf("IMPS", "NEFT")
        private val STATUS_QUERY = mapOf(FundStatus.Pending to "pending", FundStatus.Approved to "transferred", FundStatus.Declined to "declined")
    }
}

// ---------------------------------------------------------------- mapping helpers (internal for tests)

/** "1,234.50", "23.00", 23 or null to BigDecimal. Anything unreadable is null, never a crash. */
internal fun JsonPrimitive?.amount(): BigDecimal? =
    this?.contentOrNull?.replace(",", "")?.replace("₹", "")?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

internal fun JsonPrimitive?.flag(): Boolean? {
    if (this == null) return null
    booleanOrNull?.let { return it }
    return when (contentOrNull?.trim()?.lowercase()) {
        "1", "true", "yes", "y", "one", "verified" -> true
        "0", "false", "no", "n", "zero" -> false
        else -> null
    }
}

internal fun LimitsDto?.toLimits(fallback: Limits): Limits {
    val min = this?.minAmount.amount()
    val max = this?.maxAmount.amount()
    return if (min != null && max != null && min.signum() > 0 && max >= min) Limits(min, max) else fallback
}

private val LONG_NUMBER = Regex("""\d[\d ]{7,22}\d""")

/** Account or Aadhaar numbers inside ledger text become ••••last4 before they are shown. */
internal fun maskLongNumbers(text: String): String = LONG_NUMBER.replace(text) { m ->
    val digits = m.value.filter(Char::isDigit)
    if (digits.length >= 9) Masking.account(digits) else m.value
}

internal fun LedgerDto.toEntry(): WalletEntry? {
    val id = id ?: return null
    val credit = creditAmt.amount() ?: BigDecimal.ZERO
    val debit = debitAmt.amount() ?: BigDecimal.ZERO
    return WalletEntry(
        id, maskLongNumbers(transdesc?.trim()?.ifEmpty { null } ?: service?.trim()?.ifEmpty { null } ?: "Transaction"),
        transDate?.trim().orEmpty(), transTime?.trim().orEmpty(), if (debit.signum() > 0) debit.negate() else credit,
    )
}

/** Fund requests always go to Admin: the Admin option's id, or "1" (the backend's default) if none is listed. */
internal fun adminOf(options: List<RequestFromDto>): String =
    (options.firstOrNull { it.role?.contains("admin", ignoreCase = true) == true || it.name?.contains("admin", ignoreCase = true) == true }
        ?: options.firstOrNull())?.id?.contentOrNull?.trim()?.ifEmpty { null } ?: "1"

internal fun CompanyBankDto.toBank(): CompanyBank? {
    val id = id?.contentOrNull?.toLongOrNull() ?: return null
    val acc = accountNumber?.trim().orEmpty()
    val name = bankName?.trim()?.ifEmpty { null } ?: displayName?.trim()?.ifEmpty { null } ?: return null
    return CompanyBank(id, name, acc, ifscCode?.trim()?.ifEmpty { null })
}

internal fun FundRequestRowDto.toItem(): FundRequestItem? {
    val id = id ?: return null
    val amount = requestAmount.amount() ?: amount.amount() ?: return null
    val label = (statusLabel ?: status)?.trim()?.lowercase().orEmpty()
    val status = when {
        declineStatus.flag() == true || label.startsWith("decline") || label.startsWith("reject") -> FundStatus.Declined
        transferStatus.flag() == true || label.startsWith("approve") || label.startsWith("transfer") || label == "success" -> FundStatus.Approved
        else -> FundStatus.Pending
    }
    return FundRequestItem(
        id = id, ref = tranNo?.trim()?.ifEmpty { null } ?: "FR-$id", amount = amount,
        mode = FundMode.of(paymentMode ?: pwerby), bankId = bankId?.contentOrNull?.toLongOrNull(),
        bankName = (bankName ?: bankDetails?.bankName)?.trim()?.ifEmpty { null },
        accountLast4 = (accountNo ?: bankDetails?.accountNumber)?.filter(Char::isLetterOrDigit)?.takeLast(4)?.ifEmpty { null },
        utr = tranId?.trim()?.takeIf { it != "-" }.orEmpty(), depositDate = depositDate?.trim().orEmpty(), remarks = remarks?.trim()?.ifEmpty { null },
        adminRemarks = adminRemarks?.trim()?.takeIf { it.isNotEmpty() && it != "-" }, sentDate = tranDate?.trim().orEmpty(), sentTime = tranTime?.trim().orEmpty(),
        status = status,
    )
}

internal fun fallbackSummary(items: List<FundRequestItem>): FundSummary =
    items.filter { it.status == FundStatus.Pending }.let { p -> FundSummary(p.size, p.fold(BigDecimal.ZERO) { a, i -> a + i.amount }) }

internal fun summaryOf(meta: JsonElement?): FundSummary? {
    val s = (meta as? JsonObject)?.get("summary") as? JsonObject ?: return null
    val count = s["totalPendingRequests"]?.jsonPrimitive.amount()?.toInt() ?: return null
    return FundSummary(count, s["totalPendingAmount"]?.jsonPrimitive.amount() ?: BigDecimal.ZERO)
}

internal fun refOf(data: JsonElement): String? {
    val o = data as? JsonObject ?: return null
    return listOf("tranNo", "requestId", "id").firstNotNullOfOrNull { k -> (o[k] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } }
        ?.let { if (it.all(Char::isDigit)) "FR-$it" else it }
}

/**
 * Reads the PayU CheckoutPro fields from the Add money response. The backend is adding them; this accepts the
 * fields either flat or inside `payu` / `payuParams` / `sdkParams`. Without `key`, `txnid` and `hash` the order
 * can't be opened, so it is [OrderResult.NotSupportedYet] and nothing is charged.
 */
/** What the top-up is for, sent with every PayU initiate (fixed; never user text). */
internal const val PAYMENT_PURPOSE = "ASL Wallet Topup"

internal object PayUFields {
    private val NESTED = listOf("payu", "payuParams", "sdkParams", "payuPaymentParams")
    val REQUIRED = listOf("key", "txnid", "amount", "productinfo", "firstname", "email", "surl", "furl", "hash")

    fun read(data: JsonObject, amount: BigDecimal): OrderResult {
        val src = NESTED.firstNotNullOfOrNull { runCatching { data[it]?.jsonObject }.getOrNull() } ?: data
        val params = src.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()
        if (REQUIRED.any { params[it].isNullOrBlank() }) return OrderResult.NotSupportedYet
        // The status API is GET payments/payu/{clientTxnId}/status (backend collection); clientTxnId sits beside the payu fields.
        val clientTxnId = listOf(data, src).firstNotNullOfOrNull { (it["clientTxnId"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        val txnid = params.getValue("txnid")
        return OrderResult.Ready(PayUOrder(txnid, params["amount"]?.toBigDecimalOrNull() ?: amount, params, statusId = clientTxnId ?: txnid))
    }
}

// ---------------------------------------------------------------- payout collection readers (internal for tests)

/** With allowEmptyData a success may carry no data (Unit placeholder): read it as JSON only when it is JSON. */
private fun NetworkResult.Success<JsonElement>.body(): JsonElement? = (data as Any?) as? JsonElement

private fun JsonObject.text(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "-" && !it.equals("null", true) } }

private fun JsonElement?.rows(): List<JsonObject> = when (this) {
    is kotlinx.serialization.json.JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> ((this["banks"] ?: this["data"] ?: this["accounts"]) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    else -> emptyList()
}

/**
 * payout-bank-list (UAT 10 Nov 2026): `[{id, accno (full), ifsccode, holdername, acctype, isverify "ZERO"/"ONE",
 * status "ONE", bankName}]`. Inactive rows are left out.
 */
internal fun readPayoutBanks(e: JsonElement?): List<BankAccount> = e.rows().mapNotNull { o ->
    if ((o["status"] as? JsonPrimitive).flag() == false) return@mapNotNull null
    val id = o.text("id")?.toLongOrNull() ?: return@mapNotNull null
    val acc = o.text("accno", "accountNumber", "account_number")?.filter(Char::isLetterOrDigit) ?: return@mapNotNull null
    BankAccount(
        id = id,
        bankName = o.text("bankName", "bank_name") ?: "Bank",
        accountLast4 = acc.takeLast(4),
        holder = o.text("holdername", "accountHolderName", "holderName").orEmpty(),
        ifsc = o.text("ifsccode", "ifscCode")?.uppercase().orEmpty(),
        owner = if (o.text("acctype", "accountType") == AccountOwner.Relative.api) AccountOwner.Relative else AccountOwner.Self,
        verified = ((o["isverify"] ?: o["isVerified"] ?: o["is_verified"]) as? JsonPrimitive).flag() ?: false,
        // Only a full (unmasked) number can name the account to the server.
        accountNumber = acc.takeIf { it.all(Char::isDigit) }.orEmpty(),
    )
}

/** An answer whose inner `status` is false: the server's message. */
internal fun innerRefusal(e: JsonElement?): String? {
    val o = e as? JsonObject ?: return null
    if ((o["status"] as? JsonPrimitive)?.booleanOrNull != false) return null
    return o.text("message", "msg") ?: "The request was not completed."
}

/** payout answer: the status word decides; anything unclear is Pending (never "sent" by guess). */
internal fun readPayoutResult(e: JsonElement?, account: SendAccount, amount: BigDecimal, mode: String): SendOutcome {
    val top = e as? JsonObject ?: return SendOutcome.Pending(null)
    val o = (top["data"] as? JsonObject) ?: top
    val receipt = SendReceipt(
        amount = (o["amount"] as? JsonPrimitive).amount() ?: amount, charge = (o["charge"] as? JsonPrimitive ?: o["serviceCharge"] as? JsonPrimitive).amount(),
        totalDebited = (o["totalDebited"] as? JsonPrimitive).amount(),
        holder = o.text("beneficiaryName", "holdername") ?: account.holder, bankName = o.text("bankName") ?: account.bankName,
        accountLast4 = account.accountLast4, mode = o.text("paymentMode", "paytype", "tranMode") ?: mode,
        bankRef = o.text("utr", "bankRrn", "rrn", "refno"), txnId = o.text("servid", "txnId", "orderId", "transactionId"), time = "",
    )
    innerRefusal(top)?.let { return SendOutcome.Failed(it) }
    innerRefusal(o)?.let { return SendOutcome.Failed(it) }
    val word = o.text("tranStatus", "txnStatus", "txnstatus", "payoutStatus") ?: (o["status"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return when (word?.uppercase()?.replace(" ", "")) {
        "SUCCESS", "SUCCESSFUL", "COMPLETED" -> SendOutcome.Sent(receipt)
        "FAILED", "FAILURE", "DECLINED", "REJECTED" -> SendOutcome.Failed(o.text("message", "remark"))
        else -> SendOutcome.Pending(receipt)
    }
}
