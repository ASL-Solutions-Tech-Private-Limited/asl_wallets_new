package com.asl.wallet.feature.reports.data

import com.asl.wallet.core.common.format.Masking
import com.asl.wallet.core.network.safeServerMessage
import com.asl.wallet.feature.reports.domain.Complaint
import com.asl.wallet.feature.reports.domain.Day
import com.asl.wallet.feature.reports.domain.Field
import com.asl.wallet.feature.reports.domain.LedgerEntry
import com.asl.wallet.feature.reports.domain.ReportKind
import com.asl.wallet.feature.reports.domain.ReportRow
import com.asl.wallet.feature.reports.domain.RowStatus
import com.asl.wallet.feature.reports.domain.Statuses
import com.asl.wallet.feature.reports.domain.Summary
import com.asl.wallet.feature.reports.domain.TotalKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal

/*
 * Report rows are read field by field into app models. Only what the screens show is read: provider logs
 * (responseLog, vendorResponseLog, tranMsg, tranMessage, tranApi, vendorTxnId) are never touched.
 */

internal fun JsonObject.str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
    (this[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "-" && !it.equals("null", true) && !it.equals("N/A", true) }
}

internal fun JsonObject.amt(vararg keys: String): BigDecimal? = keys.firstNotNullOfOrNull { k ->
    (this[k] as? JsonPrimitive)?.contentOrNull?.replace(",", "")?.trim()?.toBigDecimalOrNull()
}

private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.let { s -> s == "1" || s.equals("true", true) } }

/** "06:51:47 PM " → "6:51 PM"; "14:30:38" → "2:30 PM". Anything else is kept as is. */
internal fun cleanTime(raw: String?): String? {
    val t = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val m = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?\s*([AaPp][Mm])?$""").find(t) ?: return t
    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2]
    val ap = m.groupValues[3].uppercase()
    val suffix = if (ap.isNotEmpty()) ap else if (h >= 12) "PM" else "AM"
    if (ap.isEmpty()) h = if (h % 12 == 0) 12 else h % 12
    return "${h.toString().trimStart('0').ifEmpty { "12" }}:$min $suffix"
}

/** Last 4 digits only, whatever the server sent (it may already be masked). */
internal fun last4(raw: String?): String? = raw?.filter(Char::isDigit)?.takeIf { it.length >= 4 }?.takeLast(4)

/** "Cash Withdrawal" → "CW"; one word ("JIO") → "JI". */
internal fun initials(s: String?): String {
    val words = s.orEmpty().split(' ', '·').map { it.trim() }.filter { w -> w.any(Char::isLetter) }
    return when {
        words.isEmpty() -> "TX"
        words.size == 1 -> words[0].filter(Char::isLetterOrDigit).take(2).uppercase()
        else -> words.take(2).joinToString("") { it.first { ch -> ch.isLetterOrDigit() }.uppercase() }
    }
}

private fun complaintOf(o: JsonObject): Complaint? =
    o.str("complainText", "complaintText")?.let { Complaint(it, o.str("complainDate", "complaintDate"), o.str("followupRemarks")) }

private fun MutableList<Pair<Field, String>>.add(f: Field, v: String?) { if (!v.isNullOrBlank()) add(f to v) }

private fun money(a: BigDecimal?): String? = a?.let { com.asl.wallet.core.common.format.Money.format(it) }

fun readRow(kind: ReportKind, o: JsonObject): ReportRow? {
    val servid = o.str("servid", "servId", "id") ?: return null
    val day = Day.parse(o.str("trandate", "tranDate"))
    val time = cleanTime(o.str("trantime", "tranTime"))
    val f = mutableListOf<Pair<Field, String>>()
    return when (kind) {
        ReportKind.Recharge -> {
            val no = o.str("rechargeNo")
            val isMobile = no != null && no.length == 10 && no.all(Char::isDigit)
            val op = o.str("operatorName") ?: "Recharge"
            val ref = o.str("operatorRefNo")
            // Recharge report shows the full mobile / subscriber number (client request — the retailer needs it to match the recharge).
            f.add(Field.Operator, op); f.add(if (isMobile) Field.Number else Field.SubscriberId, no)
            f.add(Field.OperatorRef, ref); f.add(Field.TxnId, servid)
            ReportRow(kind, servid, listOfNotNull(op, no).joinToString(" · "), "Ref ${ref ?: "—"}", o.amt("amount"), Statuses.of(o.str("tranStatus")), day, time, f, complaintOf(o), initials(op))
        }
        ReportKind.Payout -> {
            val name = o.str("reqBene", "beneficiaryName") ?: "Bank transfer"
            val bank = o.str("bankName")
            val acc = last4(o.str("accountNumber"))
            val status = Statuses.of(o.str("tranStatus"))
            f.add(Field.Recipient, name); f.add(Field.Bank, listOfNotNull(bank, acc?.let { "••$it" }).joinToString(" ")); f.add(Field.Ifsc, o.str("reqIfsc"))
            f.add(Field.Mode, o.str("tranMode")); f.add(Field.Charge, money(o.amt("tranCharges"))); f.add(Field.Utr, o.str("bankRrn"))
            f.add(Field.OrderId, o.str("orderId")); f.add(Field.TxnId, servid)
            if (status == RowStatus.Failed) f.add(Field.Reason, safeServerMessage(o.str("tranRemarks")))
            ReportRow(kind, servid, listOfNotNull(name, listOfNotNull(bank?.let(::shortBank), acc?.let { "••$it" }).joinToString(" ").ifEmpty { null }).joinToString(" · "),
                listOfNotNull(o.str("tranMode"), "UTR ${o.str("bankRrn") ?: "—"}").joinToString(" · "), o.amt("amount"), status, day, time, f, complaintOf(o), initials(name))
        }
        ReportKind.Aeps -> {
            val type = o.str("tranTypeName", "tranType") ?: "AePS"
            val bank = o.str("bankName")
            val aadhaar = last4(o.str("reqAadhaar"))?.let { "••$it" }
            f.add(Field.Type, type); f.add(Field.Bank, bank); f.add(Field.Aadhaar, aadhaar); f.add(Field.Rrn, o.str("bankRrn"))
            f.add(Field.CustomerBalance, o.amt("balanceAmt")?.let(::money)); f.add(Field.TxnId, servid)
            f.add(Field.Commission, o.amt("tranCommAmt")?.takeIf { it.signum() > 0 }?.let(::money))
            ReportRow(kind, servid, listOfNotNull(type, bank?.let(::shortBank)).joinToString(" · "),
                listOfNotNull(aadhaar?.let { "Aadhaar $it" }, o.str("bankRrn")?.let { "RRN $it" }).joinToString(" · "),
                o.amt("amount")?.takeIf { it.signum() > 0 }, Statuses.of(o.str("tranStatus")), day, time, f, complaintOf(o), initials(type))
        }
        ReportKind.Matm -> {
            val type = o.str("serviceTypeName", "tranMode") ?: "Micro ATM"
            val card = last4(o.str("cardNumber"))?.let { "••$it" }
            f.add(Field.Type, type); f.add(Field.Card, card); f.add(Field.Mobile, o.str("reqMobno")?.let(Masking::mobile)); f.add(Field.Rrn, o.str("bankRrn"))
            f.add(Field.CustomerBalance, o.amt("balanceAmt")?.let(::money)); f.add(Field.TxnId, servid)
            f.add(Field.Commission, o.amt("tranCommAmt")?.takeIf { it.signum() > 0 }?.let(::money))
            ReportRow(kind, servid, type, listOfNotNull(card?.let { "Card $it" }, o.str("bankRrn")?.let { "RRN $it" }).joinToString(" · "),
                o.amt("amount")?.takeIf { it.signum() > 0 }, Statuses.of(o.str("tranStatus")), day, time, f, complaintOf(o), initials(type))
        }
        ReportKind.Pan -> {
            val name = o.str("appName") ?: listOfNotNull(o.str("appFirstName"), o.str("appLastName")).joinToString(" ").ifEmpty { "PAN application" }
            f.add(Field.Applicant, name); f.add(Field.PanType, o.str("panTypeLabel")); f.add(Field.PanMode, o.str("appModeLabel"))
            f.add(Field.ApplicationNo, o.str("applicationNo")); f.add(Field.TxnId, servid)
            ReportRow(kind, servid, name, listOfNotNull(o.str("panTypeLabel"), o.str("appModeLabel"), o.str("applicationNo")).joinToString(" · "),
                o.amt("appCharges"), Statuses.of(o.str("tranStatus")), Day.parse(o.str("tranDate")), cleanTime(o.str("tranTime")), f, complaintOf(o), initials(name),
                serverAllowsComplaint = o.bool("canComplain") != false)
        }
        ReportKind.AddMoney -> {
            // UAT 10 Nov 2026: {id, clientTxnId, orderId, amount, apiTag "PAYU", status SUCCESS/PENDING/FAILED,
            // isPaymentComp ONE/ZERO, pInfo (the failure text on failures), inDate "10/11/2026", inTime "3:47:50 pm"}.
            // The customer e-mail and mobile in each row are never read.
            val txn = o.str("clientTxnId", "txnid") ?: servid
            val via = o.str("apiTag")?.let { if (it.equals("PAYU", true)) "PayU" else it } ?: "PayU"
            val status = Statuses.of(o.str("status"))
            f.add(Field.Mode, via); f.add(Field.OrderId, o.str("orderId")); f.add(Field.TxnId, txn)
            if (status == RowStatus.Failed) f.add(Field.Reason, o.str("pInfo")?.removePrefix("FAILED:")?.trim()?.takeIf { it.isNotEmpty() && !it.equals("No Error", true) }?.let(::safeServerMessage))
            ReportRow(kind, txn, "Wallet top-up · $via", txn, o.amt("amount"), status,
                Day.parse(o.str("inDate")), cleanTime(o.str("inTime")), f, null, "AM")
        }
        ReportKind.Dmt -> {
            val name = o.str("beneficiaryName")?.replace(Regex("\\s+"), " ") ?: "Money transfer"
            val bank = o.str("beneficiaryBank")
            val acc = last4(o.str("beneficiaryAccountNumber"))?.let { "••$it" }
            // The report doesn't name the customer yet (fname/phone are the retailer): read it only if the server adds it.
            val customer = o.str("remitterName", "senderName", "customerName", "custName")
            val customerMobile = o.str("remitterMobile", "senderMobile", "customerMobile")?.let(Masking::mobile)
            val status = Statuses.of(o.str("tranStatus"), o.str("refundStatus"))
            f.add(Field.Customer, listOfNotNull(customer, customerMobile).joinToString(" · ").ifEmpty { null })
            f.add(Field.Recipient, name); f.add(Field.Bank, listOfNotNull(bank, acc).joinToString(" ")); f.add(Field.Ifsc, o.str("beneficiaryIfsc"))
            f.add(Field.Mode, o.str("tranMode")); f.add(Field.Charge, money(o.amt("charges"))); f.add(Field.Utr, o.str("bankRrn"))
            if (status == RowStatus.Refunded) f.add(Field.Refund, "Refunded to wallet")
            f.add(Field.TxnId, servid)
            ReportRow(kind, servid, listOfNotNull(name, listOfNotNull(bank?.let(::shortBank), acc).joinToString(" ").ifEmpty { null }).joinToString(" · "),
                listOfNotNull(customer, o.str("tranMode"), "UTR ${o.str("bankRrn") ?: "—"}").joinToString(" · "),
                o.amt("amount"), status, day, time, f, null, initials(name))
        }
    }
}

/** "STATE BANK OF INDIA" → "State Bank Of India" is long; common names are shortened for the row only. */
internal fun shortBank(b: String): String {
    val u = b.uppercase().replace(Regex("\\s+"), " ").trim()
    return SHORT[u] ?: b.trim().split(' ').joinToString(" ") { w -> if (w.length <= 4 && w == w.uppercase()) w else w.lowercase().replaceFirstChar(Char::uppercase) }
}

private val SHORT = mapOf(
    "STATE BANK OF INDIA" to "SBI", "PUNJAB NATIONAL BANK" to "PNB", "BANK OF BARODA" to "BoB", "HDFC BANK" to "HDFC",
    "ICICI BANK" to "ICICI", "AXIS BANK" to "Axis", "UCO BANK" to "UCO", "IDBI BANK" to "IDBI", "CANARA BANK" to "Canara",
    "UNION BANK OF INDIA" to "Union Bank", "BANK OF INDIA" to "BOI", "KOTAK MAHINDRA BANK" to "Kotak",
)

/**
 * Ledger descriptions carry full account / card / Aadhaar numbers ("MONEY TRANSFER TO A/C : 0185104000179539").
 * Any standalone run of 9+ digits becomes ••last4, so they never reach a screen, a share or a PDF.
 * IDs that mix letters and digits (DMT1793712188141) are not touched.
 */
internal fun maskDigits(s: String): String = s.replace(Regex("""(?<![A-Za-z0-9])\d{9,}(?![A-Za-z0-9])""")) { "••" + it.value.takeLast(4) }

fun readLedger(o: JsonObject): LedgerEntry? {
    val id = o.str("id")?.toLongOrNull() ?: return null
    return LedgerEntry(
        id = id,
        description = o.str("transdesc", "transDesc", "description")?.replace(Regex("\\s+"), " ")?.let(::maskDigits) ?: "Wallet entry",
        day = Day.parse(o.str("transDate", "trandate")),
        time = cleanTime(o.str("transTime", "trantime")),
        credit = o.amt("creditAmt") ?: BigDecimal.ZERO,
        debit = o.amt("debitAmt") ?: BigDecimal.ZERO,
        opening = o.amt("openingBal"),
        closing = o.amt("closingBal"),
        reference = o.str("referenceId"),
    )
}

/** meta.summary of each report, in one shape. Totals the server didn't send are left out, never added up here. */
fun readSummary(kind: ReportKind?, meta: JsonElement?, totalFromPaging: Int?): Summary? {
    val s = ((meta as? JsonObject)?.get("summary") as? JsonObject) ?: return totalFromPaging?.let { Summary(it, emptyList()) }
    val count = s.str("totalCount", "totalTransactions", "totalRecords")?.toIntOrNull() ?: totalFromPaging
    val t = mutableListOf<Pair<TotalKind, BigDecimal>>()
    fun put(k: TotalKind, v: BigDecimal?) { if (v != null) t += k to v }
    when (kind) {
        null -> { put(TotalKind.MoneyIn, s.amt("totalCredit")); put(TotalKind.MoneyOut, s.amt("totalDebit")) }
        ReportKind.Recharge -> put(TotalKind.Amount, s.amt("totalAmount"))
        ReportKind.Payout -> { put(TotalKind.Amount, s.amt("sumAmt", "totalAmount")) }
        ReportKind.Aeps -> { put(TotalKind.Amount, s.amt("totalCwAmount")); put(TotalKind.Commission, s.amt("totalCwCommission")) }
        ReportKind.Matm -> { put(TotalKind.Amount, s.amt("totalAmount")); put(TotalKind.Commission, s.amt("totalCommission")) }
        ReportKind.Pan -> put(TotalKind.Amount, s.amt("totalCharges"))
        ReportKind.Dmt -> { put(TotalKind.Amount, s.amt("totalAmount")); put(TotalKind.Success, s.amt("successAmount")); put(TotalKind.Failed, s.amt("failureAmount")) }
        ReportKind.AddMoney -> {
            put(TotalKind.Amount, s.amt("totalAmount")); put(TotalKind.Success, s.amt("successAmount")); put(TotalKind.Pending, s.amt("pendingAmount"))
        }
    }
    return Summary(count, t)
}

/** Which service reports this retailer has, from `merchant/services` names. */
fun kindsFromServices(rows: List<JsonObject>): Set<ReportKind> {
    val on = rows.filter { o -> (o["status"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase().let { it == null || it in setOf("1", "true", "active", "yes") } }
        .mapNotNull { it.str("services_name", "serviceName", "name")?.lowercase() }
    val kinds = mutableSetOf<ReportKind>()
    on.forEach { n ->
        when {
            "micro atm" in n || "matm" in n || "mini atm" in n -> kinds += ReportKind.Matm
            "aeps" in n || "aadhaar enabled" in n -> kinds += ReportKind.Aeps
            "dmt" in n || "money transfer" in n || "domestic" in n || "remit" in n -> kinds += ReportKind.Dmt
            "payout" in n || "settlement" in n -> kinds += ReportKind.Payout
            "pan" in n.split(Regex("[^a-z]+")) || "nsdl" in n || "uti" in n.split(Regex("[^a-z]+")) -> kinds += ReportKind.Pan
            "recharge" in n || "prepaid" in n || "dth" in n || "mobile" in n -> kinds += ReportKind.Recharge
        }
    }
    // Send to bank is a wallet feature every retailer has (Module 4), so its report always shows.
    kinds += ReportKind.Payout
    // Add money (PayU top-ups) is a wallet feature too.
    kinds += ReportKind.AddMoney
    return kinds
}
