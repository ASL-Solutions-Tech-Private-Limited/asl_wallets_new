package com.asl.wallet.feature.recharge.data

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.map
import com.asl.wallet.core.network.safeApiCall
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.feature.recharge.domain.DetectedOperator
import com.asl.wallet.feature.recharge.domain.DthCustomer
import com.asl.wallet.feature.recharge.domain.Operator
import com.asl.wallet.feature.recharge.domain.OperatorLists
import com.asl.wallet.feature.recharge.domain.Plan
import com.asl.wallet.feature.recharge.domain.PlanGroup
import com.asl.wallet.feature.recharge.domain.RecentRecharge
import com.asl.wallet.feature.recharge.domain.RechargeOutcome
import com.asl.wallet.feature.recharge.domain.RechargeReceipt
import com.asl.wallet.feature.recharge.domain.RechargeRules
import com.asl.wallet.feature.recharge.domain.RechargeStatus
import com.asl.wallet.feature.recharge.domain.RechargeStatuses
import com.asl.wallet.feature.recharge.domain.RechargeType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** One recharge the retailer confirmed. [key] is made once per confirmed recharge and never reused for another. */
data class RechargeInput(
    val type: RechargeType,
    val number: String,
    val operator: Operator,
    val amount: Int,
    val customerName: String?,
    val planLine: String?,
    val key: IdempotencyKey,
)

interface RechargeRepository {
    suspend fun operators(): NetworkResult<OperatorLists>
    suspend fun detect(mobile: String): NetworkResult<DetectedOperator?>
    suspend fun plans(operator: Operator): NetworkResult<List<PlanGroup>>
    suspend fun dthInfo(operator: Operator, subscriberId: String): NetworkResult<DthCustomer?>
    /** Never throws and never retries: a timeout or 5xx is [RechargeOutcome.Pending]. */
    suspend fun recharge(input: RechargeInput): RechargeOutcome
    suspend fun recent(): NetworkResult<List<RecentRecharge>>
    suspend fun balance(): NetworkResult<BigDecimal>
}

@Singleton
class RechargeRepositoryImpl @Inject constructor(private val api: RechargeApi, private val json: Json) : RechargeRepository {
    @Volatile private var operators: OperatorLists? = null
    private val planCache = HashMap<String, List<PlanGroup>>()

    override suspend fun operators(): NetworkResult<OperatorLists> {
        operators?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.operators() }.map { o ->
            OperatorLists(readOperators(o["mobile_prepaid"] ?: o["mobile"]), readOperators(o["dth_recharge"] ?: o["dth"]))
        }.also { if (it is NetworkResult.Success && (it.data.mobile.isNotEmpty() || it.data.dth.isNotEmpty())) operators = it.data }
    }

    override suspend fun detect(mobile: String) = safeApiCall(json) { api.detect(DetectRequest(mobile)) }.map { d ->
        val code = d.OpCode?.contentOrNull?.trim()?.ifEmpty { null }
        val name = d.Operator?.trim()?.ifEmpty { null }
        val ok = d.ERROR?.contentOrNull?.trim().let { it == null || it == "0" }
        if (!ok || code == null || name == null) null
        else DetectedOperator(Operator(code, operators?.mobile?.firstOrNull { it.code == code }?.name ?: shortOperator(name)), d.Circle?.trim()?.ifEmpty { null })
    }

    override suspend fun plans(operator: Operator): NetworkResult<List<PlanGroup>> {
        synchronized(planCache) { planCache[operator.code] }?.let { return NetworkResult.Success(it, null) }
        return safeApiCall(json) { api.plans(PlansRequest(operator.code)) }.map { readPlans(it) }
            .also { r -> if (r is NetworkResult.Success && r.data.isNotEmpty()) synchronized(planCache) { planCache[operator.code] = r.data } }
    }

    override suspend fun dthInfo(operator: Operator, subscriberId: String): NetworkResult<DthCustomer?> =
        when (val r = safeApiCall(json) { api.dthInfo(DthInfoRequest(subscriberId.trim(), operator.code)) }) {
            is NetworkResult.Failure -> r
            is NetworkResult.Success -> readDthAnswer(r.data)
        }

    override suspend fun recharge(input: RechargeInput): RechargeOutcome {
        val r = safeApiCall(json) {
            api.recharge(input.key.value, RechargeRequest(input.number.trim(), input.operator.code, input.amount, input.type.rtype, input.customerName?.trim()?.ifEmpty { null }))
        }
        fun receipt(ref: String? = null, txn: String? = null) = RechargeReceipt(
            input.type, input.number, input.operator.name, BigDecimal(input.amount), input.planLine, input.customerName?.trim()?.ifEmpty { null }, ref, txn, "",
        )
        return when (r) {
            is NetworkResult.Success -> {
                val o = (r.data as? JsonObject) ?: JsonObject(emptyMap())
                val status = o.str("tranStatus", "status", "tran_status", "STATUS")
                val rec = receipt(o.str("operatorRefNo", "operatorRef", "opRefNo", "tranId", "apirefid")?.takeIf { it != "-" }, o.str("servid", "servId", "txnId", "reqid", "id"))
                when (RechargeStatuses.of(status)) {
                    RechargeStatus.Success -> RechargeOutcome.Done(rec)
                    RechargeStatus.Failed -> RechargeOutcome.Failed(rec, o.str("tranMsg", "message", "remark")?.let(::safeServerMessage) ?: r.message?.let(::safeServerMessage))
                    RechargeStatus.Pending -> RechargeOutcome.Pending(rec)
                }
            }
            NetworkResult.Failure.Timeout, NetworkResult.Failure.NoInternet -> RechargeOutcome.Pending(receipt())
            is NetworkResult.Failure.Api -> if (r.httpCode >= 500) RechargeOutcome.Pending(receipt()) else RechargeOutcome.Failed(receipt(), safeServerMessage(r.message), debited = false)
            is NetworkResult.Failure -> RechargeOutcome.Failed(receipt(), null, debited = false)
        }
    }

    override suspend fun recent() = safeApiCall(json) { api.recent(1, RECENT) }.map { rows ->
        rows.mapNotNull { it.toRecent() }.take(RECENT)
    }

    override suspend fun balance(): NetworkResult<BigDecimal> = when (val r = safeApiCall(json) { api.accountInfo() }) {
        is NetworkResult.Success -> (r.data.availableBalanceAmt.amount() ?: r.data.available_balance_amt.amount())
            ?.let { NetworkResult.Success(it, null) } ?: NetworkResult.Failure.Unexpected("Balance missing")
        is NetworkResult.Failure -> r
    }

    companion object { const val RECENT = 3 }
}

// ---------------------------------------------------------------- mapping (internal for tests)

internal fun JsonPrimitive?.amount(): BigDecimal? =
    this?.contentOrNull?.replace(",", "")?.replace("₹", "")?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

private fun JsonObject.str(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { k -> (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.ifEmpty { null } }

/** "Reliance Jio Infocomm Limited" → "Jio"; others are kept as the server wrote them. */
internal fun shortOperator(name: String): String {
    val n = name.lowercase()
    return when {
        "jio" in n -> "Jio"
        "airtel" in n -> "Airtel"
        "vodafone" in n || n == "vi" || "idea" in n -> "Vi"
        "bsnl" in n -> "BSNL"
        "mtnl" in n -> "MTNL"
        else -> name.trim()
    }
}

/**
 * List names as people read them, keeping each operator distinct (UAT has both VODAFONE 39 and IDEA 58, and two BSNL codes):
 * "BSNL TOPUP" → "BSNL Topup", "VIDEOCON D2H" → "Videocon D2H", "TATA SKY" → "Tata Sky".
 */
internal fun prettyOperator(name: String): String {
    val keepUpper = setOf("BSNL", "MTNL", "TV", "DTH", "D2H")
    return name.trim().split(Regex("\\s+")).joinToString(" ") { w ->
        val u = w.uppercase()
        if (u in keepUpper) u else u.lowercase().replaceFirstChar { it.titlecase() }
    }
}

/** Operator items: UAT (5 Nov) sends {optcode, optname, opt_icon}; other usual id/name keys are accepted too. */
internal fun readOperators(e: JsonElement?): List<Operator> = (e as? JsonArray).orEmpty().mapNotNull { item ->
    val o = item as? JsonObject ?: return@mapNotNull null
    val code = o.str("optcode", "opcode", "operator_code", "operatorCode", "opt_code", "code", "id") ?: return@mapNotNull null
    val name = o.str("operator_name", "operatorName", "optname", "opname", "name", "operator") ?: return@mapNotNull null
    Operator(code, prettyOperator(name))
}

/** `data.data` is a map of category → plans; each plan has type, amount, validity, description. */
internal fun readPlans(o: JsonObject): List<PlanGroup> {
    val inner = (o["data"] as? JsonObject) ?: o
    val plans = inner.flatMap { (key, v) ->
        (v as? JsonArray).orEmpty().mapNotNull { p ->
            val po = p as? JsonObject ?: return@mapNotNull null
            val amount = (po["amount"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toBigDecimalOrNull()?.toInt() } ?: return@mapNotNull null
            if (amount !in RechargeRules.MIN_AMOUNT..RechargeRules.MAX_AMOUNT) return@mapNotNull null
            Plan(
                amount = amount,
                validity = RechargeRules.cleanValidity(po.str("validity") ?: "—"),
                description = RechargeRules.cleanText(po.str("description", "desc") ?: ""),
                category = po.str("type") ?: key,
            )
        }
    }
    return RechargeRules.group(plans)
}

/** Case-insensitive lookup: providers spell keys as CustomerName / customerName / customer_name. */
private fun JsonObject.any(vararg keys: String): JsonPrimitive? {
    val wanted = keys.map { it.lowercase().replace("_", "") }
    return entries.firstOrNull { (k, v) -> v is JsonPrimitive && k.lowercase().replace("_", "") in wanted && v.contentOrNull?.isNotBlank() == true }?.value as? JsonPrimitive
}

/**
 * The customer-info answer: `{error:"0", DATA:{…}, Message}` found; error "1" a bad ID with a reason for the retailer;
 * "3" or Message "DOWN" the provider is down. Found details may sit in DATA, DATA.records[0] or DATA[0].
 */
internal fun readDthAnswer(o: JsonObject): NetworkResult<DthCustomer?> {
    val code = (o["error"] as? JsonPrimitive)?.contentOrNull?.trim()
    val message = (o["Message"] as? JsonPrimitive ?: o["message"] as? JsonPrimitive)?.contentOrNull?.trim()
    val down = code == "3" || message.equals("DOWN", ignoreCase = true)
    if (down) return NetworkResult.Failure.Api(503, "DTH_DOWN", "DOWN")
    if (code != null && code != "0") return NetworkResult.Failure.Api(422, "DTH_ID", message?.let(::safeServerMessage) ?: "")
    val data = o["DATA"] ?: o["data"]
    val src = (data as? JsonObject)?.let { d -> ((d["records"] as? JsonArray)?.firstOrNull() as? JsonObject) ?: d }
        ?: ((data as? JsonArray)?.firstOrNull() as? JsonObject)
        ?: return NetworkResult.Success(null, message)
    return NetworkResult.Success(readDthCustomer(src), message)
}

internal fun readDthCustomer(d: JsonObject): DthCustomer? {
    val c = DthCustomer(
        name = d.any("customerName", "name", "customer_name", "custName")?.contentOrNull?.trim(),
        pack = d.any("planname", "packName", "planName", "pack", "plan")?.contentOrNull?.trim(),
        status = d.any("status", "accountStatus")?.contentOrNull?.trim(),
        balance = d.any("balance", "accountBalance").amount(),
        monthly = d.any("MonthlyRecharge", "monthlyAmount", "monthly", "monthlyRechargeAmount").amount(),
        nextRecharge = d.any("NextRechargeDate", "nextRechargeDate", "next_recharge_date", "dueDate")?.contentOrNull?.trim(),
    )
    return c.takeIf { it.name != null || it.monthly != null || it.pack != null }
}

internal fun ReportRowDto.toRecent(): RecentRecharge? {
    val number = rechargeNo?.trim()?.ifEmpty { null } ?: return null
    val amount = amount.amount() ?: return null
    return RecentRecharge(
        number, shortOperator(operatorName?.trim().orEmpty().ifEmpty { "—" }), operatorCode?.contentOrNull?.trim()?.ifEmpty { null },
        amount, RechargeStatuses.of(tranStatus), trandate?.trim().orEmpty(), trantime?.trim().orEmpty(),
    )
}
