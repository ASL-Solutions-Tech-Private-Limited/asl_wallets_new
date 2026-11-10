package com.asl.wallet.feature.reports

import androidx.lifecycle.SavedStateHandle
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.reports.data.LedgerQuery
import com.asl.wallet.feature.reports.data.ReportQuery
import com.asl.wallet.feature.reports.data.ReportsApi
import com.asl.wallet.feature.reports.data.ReportsRepository
import com.asl.wallet.feature.reports.data.ReportsRepositoryImpl
import com.asl.wallet.feature.reports.data.StatementData
import com.asl.wallet.feature.reports.data.cleanTime
import com.asl.wallet.feature.reports.data.initials
import com.asl.wallet.feature.reports.data.kindsFromServices
import com.asl.wallet.feature.reports.data.maskDigits
import com.asl.wallet.feature.reports.data.readLedger
import com.asl.wallet.feature.reports.data.readRow
import com.asl.wallet.feature.reports.data.readSummary
import com.asl.wallet.feature.reports.data.shortBank
import com.asl.wallet.feature.reports.domain.Complaint
import com.asl.wallet.feature.reports.domain.Day
import com.asl.wallet.feature.reports.domain.Field
import com.asl.wallet.feature.reports.domain.LedgerEntry
import com.asl.wallet.feature.reports.domain.LedgerType
import com.asl.wallet.feature.reports.domain.Period
import com.asl.wallet.feature.reports.domain.Periods
import com.asl.wallet.feature.reports.domain.Preset
import com.asl.wallet.feature.reports.domain.ReportKind
import com.asl.wallet.feature.reports.domain.ReportPage
import com.asl.wallet.feature.reports.domain.ReportRow
import com.asl.wallet.feature.reports.domain.ReportRules
import com.asl.wallet.feature.reports.domain.RowStatus
import com.asl.wallet.feature.reports.domain.Statuses
import com.asl.wallet.feature.reports.domain.TotalKind
import com.asl.wallet.feature.reports.ui.ComplaintState
import com.asl.wallet.feature.reports.ui.LedgerViewModel
import com.asl.wallet.feature.reports.ui.ReportViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
private fun obj(s: String): JsonObject = json.parseToJsonElement(s).jsonObject
private val nov6 = Day(2026, 11, 6)
private val novPeriod = Period(Day(2026, 11, 1), nov6, Preset.ThisMonth)

/** A real UAT recharge row (6 Nov 2026), including the provider log fields the app must ignore. */
private val RECHARGE_ROW = """{"index":1,"id":"602754","servid":"8214758921330010","trandate":"05-11-2026","trantime":"14:30:38","operatorName":"JIO",
 "operatorCode":"56","rechargeNo":"8709305218","amount":55,"tranStatus":"FAILED","tranId":"-","tranMsg":"LOGIN FAILED FROM IP 10.1.2.3",
 "operatorRefNo":"-","complainText":"","complainDate":"","followupRemarks":"","vendorResponseLog":"<xml>secret</xml>","responseLog":"raw"}"""

private val DMT_ROW = """{"id":193052,"servid":"DMT1793712188141","orderId":"","fname":"Ajay","lname":"Kumar","phone":"9798143077","regno":"6044",
 "tranDate":"2026-11-03","tranTime":"18:53:08","beneficiaryName":"AJAY  KUMAR","beneficiaryBank":"IDBI BANK","beneficiaryIfsc":"IBKL0000185",
 "beneficiaryAccountNumber":"0185104000179539","beneficiaryMobile":"9798143077","tranMode":"IMPS","amount":100,"charges":0,"tranStatus":"FAILURE",
 "bankRrn":null,"tranMessage":"Transaction Success (Mock Response)","responseLog":null,"tranApi":"ASL CORPORATE PROVIDER","refundStatus":"REFUNDED"}"""

class ReportRulesTest {
    @Test fun `days - month arithmetic, labels and parsing`() {
        assertThat(Day(2026, 3, 1).plusDays(-1)).isEqualTo(Day(2026, 2, 28))
        assertThat(Day(2026, 5, 31).minusMonths(3)).isEqualTo(Day(2026, 2, 28))
        assertThat(Day(2026, 11, 6).monthEnd()).isEqualTo(Day(2026, 11, 30))
        assertThat(nov6.iso()).isEqualTo("2026-11-06")
        assertThat(nov6.label()).isEqualTo("6 Nov 2026")
        assertThat(Day.parse("05/11/2026")).isEqualTo(Day(2026, 11, 5))
        assertThat(Day.parse("2026-11-03")).isEqualTo(Day(2026, 11, 3))
        assertThat(Day.parse("bad")).isNull()
    }

    @Test fun `presets - this month is the default, last month is a full month`() {
        assertThat(Periods.default(nov6)).isEqualTo(Period(Day(2026, 11, 1), nov6, Preset.ThisMonth))
        assertThat(Periods.of(Preset.LastMonth, nov6)).isEqualTo(Period(Day(2026, 10, 1), Day(2026, 10, 31), Preset.LastMonth))
        assertThat(Periods.of(Preset.Last7, nov6).from).isEqualTo(Day(2026, 10, 31))
        assertThat(Periods.of(Preset.Yesterday, Day(2026, 1, 1)).from).isEqualTo(Day(2025, 12, 31))
        assertThat(novPeriod.label()).isEqualTo("1 Nov – 6 Nov 2026")
    }

    @Test fun `ranges - capped at 3 months, no future, mATM and PAN can't go back further than 3 months`() {
        assertThat(Periods.check(Day(2026, 8, 6), nov6, nov6, false)).isEqualTo(Periods.Check.Ok)
        assertThat(Periods.check(Day(2026, 8, 5), nov6, nov6, false)).isEqualTo(Periods.Check.TooLong)
        assertThat(Periods.check(nov6, Day(2026, 11, 1), nov6, false)).isEqualTo(Periods.Check.FromAfterTo)
        assertThat(Periods.check(nov6, Day(2026, 11, 7), nov6, false)).isEqualTo(Periods.Check.InFuture)
        assertThat(Periods.check(Day(2026, 5, 1), Day(2026, 6, 1), nov6, false)).isEqualTo(Periods.Check.Ok)
        assertThat(Periods.check(Day(2026, 5, 1), Day(2026, 6, 1), nov6, true)).isEqualTo(Periods.Check.TooOld)
    }

    @Test fun `statuses - never success by guess, refunds recognised`() {
        assertThat(Statuses.of("SUCCESS")).isEqualTo(RowStatus.Success)
        assertThat(Statuses.of("failed")).isEqualTo(RowStatus.Failed)
        assertThat(Statuses.of("FAILURE", "REFUNDED")).isEqualTo(RowStatus.Refunded)
        assertThat(Statuses.of("REVERSED")).isEqualTo(RowStatus.Refunded)
        assertThat(Statuses.of("UNKNOWN")).isEqualTo(RowStatus.Pending)
        assertThat(Statuses.of(null)).isEqualTo(RowStatus.Pending)
    }

    @Test fun `search goes in the right API field`() {
        assertThat(ReportKind.Recharge.searchParam("8709305218")).isEqualTo("rechargeNo")
        assertThat(ReportKind.Recharge.searchParam("8214758921330010")).isEqualTo("tranId")
        assertThat(ReportKind.Aeps.searchParam("330719004877")).isEqualTo("bankRrn")
        assertThat(ReportKind.Dmt.searchParam("9798143077")).isEqualTo("mobileNumber")
        assertThat(ReportKind.Dmt.searchParam("0185104000179539")).isEqualTo("accountNumber")
        assertThat(ReportKind.Dmt.searchParam("DMT1793712188141")).isEqualTo("tranId")
        assertThat(ReportKind.Payout.searchParam("CME1148261")).isEqualTo("servid")
    }

    @Test fun `status values match each API`() {
        assertThat(ReportKind.Recharge.statusValue(RowStatus.Failed)).isEqualTo("FAILED")
        assertThat(ReportKind.Dmt.statusValue(RowStatus.Failed)).isEqualTo("FAIL") // DMT matches with "contains"
        assertThat(ReportKind.Aeps.hasStatusFilter).isFalse()
        assertThat(ReportKind.Matm.lookbackLimited).isTrue()
    }

    @Test fun `search and complaint text are cleaned and capped`() {
        assertThat(ReportRules.cleanSearch("98<script>'; DROP")).isEqualTo("98script DROP")
        assertThat(ReportRules.cleanSearch("x".repeat(60))).hasLength(40)
        assertThat(ReportRules.cleanComplaint("a<b>\nc", 100)).isEqualTo("a b  c")
        assertThat(ReportRules.cleanComplaint("y".repeat(300), 100)).hasLength(100)
    }

    @Test fun `entries are grouped by day in server order`() {
        val g = ReportRules.byDay(listOf(1 to nov6, 2 to nov6, 3 to Day(2026, 11, 5))) { it.second }
        assertThat(g.map { it.first }).containsExactly(nov6, Day(2026, 11, 5)).inOrder()
        assertThat(g[0].second.map { it.first }).containsExactly(1, 2)
    }

    @Test fun `complaints - only success or pending, only once, only where the API takes them`() {
        val r = readRow(ReportKind.Recharge, obj(RECHARGE_ROW))!!
        assertThat(r.canComplain).isFalse() // failed
        assertThat(r.copy(status = RowStatus.Success).canComplain).isTrue()
        assertThat(r.copy(status = RowStatus.Pending, complaint = Complaint("x", null, null)).canComplain).isFalse()
        assertThat(r.copy(kind = ReportKind.Dmt, status = RowStatus.Success).canComplain).isFalse()
    }
}

class ReadersTest {
    @Test fun `recharge row - masked number, no provider text, IDs kept`() {
        val r = readRow(ReportKind.Recharge, obj(RECHARGE_ROW))!!
        assertThat(r.txnId).isEqualTo("8214758921330010")
        assertThat(r.title).isEqualTo("JIO · 87XXX XX218")
        assertThat(r.status).isEqualTo(RowStatus.Failed)
        assertThat(r.amount).isEqualTo(BigDecimal(55))
        assertThat(r.day).isEqualTo(Day(2026, 11, 5))
        assertThat(r.time).isEqualTo("2:30 PM")
        assertThat(r.complaint).isNull() // empty strings are no complaint
        assertThat(r.fields.map { it.first }).doesNotContain(Field.OperatorRef) // "-" is no ref
        val all = r.toString()
        assertThat(all).doesNotContain("LOGIN FAILED"); assertThat(all).doesNotContain("10.1.2.3"); assertThat(all).doesNotContain("secret")
    }

    @Test fun `DTH subscriber IDs are shown in full`() {
        val r = readRow(ReportKind.Recharge, obj("""{"servid":"1","rechargeNo":"02906259287","operatorName":"Dish TV","amount":349,"tranStatus":"SUCCESS"}"""))!!
        assertThat(r.fields).contains(Field.SubscriberId to "02906259287")
    }

    @Test fun `DMT row - masked account, refund shown, customer only if the server sends it, provider fields ignored`() {
        val r = readRow(ReportKind.Dmt, obj(DMT_ROW))!!
        assertThat(r.status).isEqualTo(RowStatus.Refunded)
        assertThat(r.title).isEqualTo("AJAY KUMAR · IDBI ••9539")
        assertThat(r.fields).contains(Field.Bank to "IDBI BANK ••9539")
        assertThat(r.fields.map { it.first }).doesNotContain(Field.Customer) // fname/phone are the retailer
        assertThat(r.toString()).doesNotContain("Mock Response")
        assertThat(r.toString()).doesNotContain("0185104000179539")
        assertThat(r.toString()).doesNotContain("CORPORATE PROVIDER")
        val withCustomer = readRow(ReportKind.Dmt, obj(DMT_ROW.replace("\"orderId\":\"\"", "\"orderId\":\"\",\"remitterName\":\"Avijit\",\"remitterMobile\":\"8910275651\"")))!!
        assertThat(withCustomer.fields).contains(Field.Customer to "Avijit · 89XXX XX651")
    }

    @Test fun `payout row - account last 4, bank reason only when failed and safe`() {
        val row = """{"servid":"CME1","reqBene":"SUSANTA PATRA","bankName":"STATE BANK OF INDIA","accountNumber":"40447541331","tranMode":"IMPS","amount":10000,
            "tranCharges":5,"tranStatus":"SUCCESS","bankRrn":"519263502791","tranRemarks":"Transaction Successful","trandate":"2025-07-11","trantime":"07:44:05 PM"}"""
        val r = readRow(ReportKind.Payout, obj(row))!!
        assertThat(r.title).isEqualTo("SUSANTA PATRA · SBI ••1331")
        assertThat(r.fields.map { it.first }).doesNotContain(Field.Reason)
        assertThat(r.toString()).doesNotContain("40447541331")
        val failed = readRow(ReportKind.Payout, obj(row.replace("\"SUCCESS\"", "\"FAILED\"").replace("Transaction Successful", "Vendor IP 10.0.0.1 down")))!!
        assertThat(failed.fields.map { it.first }).doesNotContain(Field.Reason) // unsafe text dropped
    }

    @Test fun `AePS row - Aadhaar last 4 only, commission kept off the receipt`() {
        val r = readRow(ReportKind.Aeps, obj("""{"servid":"A1","tranTypeName":"Cash Withdrawal","bankName":"SBI","reqAadhaar":"234567894821","amount":2000,
            "balanceAmt":"10480","tranStatus":"SUCCESS","bankRrn":"330719004877","tranCommAmt":9,"vendorTxnId":"V-SECRET"}"""))!!
        assertThat(r.fields).contains(Field.Aadhaar to "••4821")
        assertThat(r.toString()).doesNotContain("234567894821")
        assertThat(r.toString()).doesNotContain("V-SECRET")
        assertThat(r.fields.map { it.first }).contains(Field.Commission)
        assertThat(r.receiptFields.map { it.first }).doesNotContain(Field.Commission)
    }

    @Test fun `ledger entry and times`() {
        val e = readLedger(obj("""{"id":7654047,"transdesc":"FUND REVERSE (DIRECT) - Fund Reverse","transDate":"05/11/2026","transTime":"06:51:47 PM ",
            "creditAmt":0,"debitAmt":100000,"openingBal":102023,"closingBal":2023,"referenceId":null}"""))!!
        assertThat(e.isCredit).isFalse()
        assertThat(e.amount).isEqualTo(BigDecimal(100000))
        assertThat(e.time).isEqualTo("6:51 PM")
        assertThat(e.day).isEqualTo(Day(2026, 11, 5))
        assertThat(cleanTime("00:05:00")).isEqualTo("12:05 AM")
        assertThat(cleanTime("12:30:00")).isEqualTo("12:30 PM")
        assertThat(shortBank("STATE BANK OF INDIA")).isEqualTo("SBI")
        assertThat(initials("JIO")).isEqualTo("JI")
        assertThat(initials("Cash Withdrawal")).isEqualTo("CW")
    }

    @Test fun `ledger descriptions never show full account numbers`() {
        assertThat(maskDigits("MONEY TRANSFER TO A/C : 0185104000179539 IFSC")).isEqualTo("MONEY TRANSFER TO A/C : ••9539 IFSC")
        assertThat(maskDigits("CHARGES FOR VERIFY (A/C : 018510400017953)")).isEqualTo("CHARGES FOR VERIFY (A/C : ••7953)")
        assertThat(maskDigits("Ref DMT1793712188141 · REQ: #131")).isEqualTo("Ref DMT1793712188141 · REQ: #131")
        val e = readLedger(obj("""{"id":1,"transdesc":"MONEY TRANSFER TO A/C : 0185104000179539","debitAmt":100}"""))!!
        assertThat(e.description).doesNotContain("0185104000179539")
    }

    @Test fun `summaries are read per report, never added up`() {
        val ledger = readSummary(null, obj("""{"summary":{"totalCredit":102120,"totalDebit":100103,"totalRecords":8}}"""), null)!!
        assertThat(ledger.count).isEqualTo(8)
        assertThat(ledger.totals.toMap()[TotalKind.MoneyIn]).isEqualTo(BigDecimal(102120))
        val aeps = readSummary(ReportKind.Aeps, obj("""{"summary":{"totalCwAmount":4000,"totalCwCommission":18,"totalCount":3}}"""), null)!!
        assertThat(aeps.totals.toMap()[TotalKind.Commission]).isEqualTo(BigDecimal(18))
        val dmt = readSummary(ReportKind.Dmt, obj("""{"summary":{"totalAmount":100,"failureAmount":100}}"""), 1)!!
        assertThat(dmt.count).isEqualTo(1)
        assertThat(readSummary(ReportKind.Recharge, null, 27)!!.count).isEqualTo(27)
    }

    @Test fun `report tiles follow the retailer's services, send to bank always`() {
        val k = kindsFromServices(listOf(obj("""{"services_name":"AEPS","status":1}"""), obj("""{"services_name":"Mobile Recharge","status":"1"}"""),
            obj("""{"services_name":"PAN Card","status":0}"""), obj("""{"services_name":"Money Transfer"}""")))
        assertThat(k).containsExactly(ReportKind.Aeps, ReportKind.Recharge, ReportKind.Dmt, ReportKind.Payout, ReportKind.AddMoney)
    }
}

private class FakeUser(regno: Long? = 6044) : CurrentUser {
    override val user = MutableStateFlow(regno?.let { UserSummary("Ajay Kumar", "ASL Payzones", it, "Retailer", true) })
    override suspend fun logout() = Unit
}

class ReportsRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: ReportsRepositoryImpl
    private val user = FakeUser()

    @Before fun setUp() {
        server.start()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient()).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = ReportsRepositoryImpl(retrofit.create(ReportsApi::class.java), json, { user })
    }
    @After fun tearDown() = server.shutdown()

    private fun ok(data: String, extra: String = "") = MockResponse().setBody("""{"status":true,"statusCode":200,"message":"ok","data":$data$extra}""")

    @Test fun `ledger sends dates, type and search, never a regno, and reads totals and paging`() = runTest {
        server.enqueue(ok("""[{"id":1,"transdesc":"A","creditAmt":5,"debitAmt":0}]""",
            ""","meta":{"summary":{"totalCredit":5,"totalDebit":0,"totalRecords":3}},"pagination":{"currentPage":1,"totalPages":2,"hasNext":true}"""))
        val r = repo.ledger(LedgerQuery(novPeriod, LedgerType.MoneyIn, search = "recharge")) as NetworkResult.Success
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/merchant/ledger-report")
        assertThat(url.queryParameter("fromDate")).isEqualTo("2026-11-01")
        assertThat(url.queryParameter("toDate")).isEqualTo("2026-11-06")
        assertThat(url.queryParameter("tranType")).isEqualTo("CREDIT")
        assertThat(url.queryParameter("search")).isEqualTo("recharge")
        assertThat(url.queryParameterNames).doesNotContain("regno")
        assertThat(url.queryParameterNames).doesNotContain("regNo")
        assertThat(r.data.canLoadMore).isTrue()
        assertThat(r.data.summary!!.count).isEqualTo(3)
    }

    @Test fun `a 1-2 character search isn't sent`() = runTest {
        server.enqueue(ok("[]"))
        repo.ledger(LedgerQuery(novPeriod, search = "98"))
        assertThat(server.takeRequest().requestUrl!!.queryParameterNames).doesNotContain("search")
    }

    @Test fun `DMT report sends only the signed-in retailer's own regNo`() = runTest {
        server.enqueue(ok("[$DMT_ROW]", ""","pagination":{"currentPage":1,"totalRecords":1,"totalPages":1,"hasNext":false}"""))
        val r = repo.report(ReportKind.Dmt, ReportQuery(novPeriod, RowStatus.Failed, "9798143077")) as NetworkResult.Success
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/dmt-transaction-report")
        assertThat(url.queryParameter("regNo")).isEqualTo("6044")
        assertThat(url.queryParameter("tranStatus")).isEqualTo("FAIL")
        assertThat(url.queryParameter("mobileNumber")).isEqualTo("9798143077")
        assertThat(r.data.items).hasSize(1)
        assertThat(r.data.canLoadMore).isFalse()
    }

    @Test fun `DMT report isn't called without a signed-in retailer`() = runTest {
        val noUser = ReportsRepositoryImpl(Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(ReportsApi::class.java), json, { FakeUser(null) })
        assertThat(noUser.report(ReportKind.Dmt, ReportQuery(novPeriod))).isEqualTo(NetworkResult.Failure.Unauthorized)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `recharge status filter and other reports' params`() = runTest {
        server.enqueue(ok("[$RECHARGE_ROW]")); server.enqueue(ok("[]"))
        repo.report(ReportKind.Recharge, ReportQuery(novPeriod, RowStatus.Success, "8709305218"))
        val u1 = server.takeRequest().requestUrl!!
        assertThat(u1.encodedPath).isEqualTo("/merchant/recharge-report")
        assertThat(u1.queryParameter("status")).isEqualTo("SUCCESS")
        assertThat(u1.queryParameter("rechargeNo")).isEqualTo("8709305218")
        assertThat(u1.queryParameterNames).doesNotContain("regNo")
        repo.report(ReportKind.Aeps, ReportQuery(novPeriod, RowStatus.Success))
        val u2 = server.takeRequest().requestUrl!!
        assertThat(u2.queryParameterNames).doesNotContain("status") // AePS has no status filter
        assertThat(u2.queryParameterNames).doesNotContain("tranStatus")
    }

    @Test fun `complaint goes to the report's own path with servid and capped text`() = runTest {
        server.enqueue(ok("{}"))
        val row = readRow(ReportKind.Recharge, obj(RECHARGE_ROW))!!.copy(status = RowStatus.Success)
        assertThat(repo.complain(row, "  " + "z".repeat(150))).isInstanceOf(NetworkResult.Success::class.java)
        val req = server.takeRequest()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.path).isEqualTo("/merchant/recharge-report/complain")
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertThat(body["servid"].toString()).isEqualTo("\"8214758921330010\"")
        assertThat(body["complainText"].toString().trim('"')).hasLength(100)
    }

    @Test fun `a refused complaint shows the server's message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"status":false,"message":"Complaints can only be filed against SUCCESS or PENDING transactions."}"""))
        val row = readRow(ReportKind.Recharge, obj(RECHARGE_ROW))!!.copy(status = RowStatus.Success)
        assertThat(repo.complain(row, "help")).isInstanceOf(NetworkResult.Failure.Api::class.java)
    }

    @Test fun `statement reads every page and returns oldest first`() = runTest {
        server.enqueue(ok("""[{"id":3,"creditAmt":1},{"id":2,"creditAmt":1}]""", ""","pagination":{"currentPage":1,"totalPages":2,"hasNext":true}"""))
        server.enqueue(ok("""[{"id":1,"creditAmt":1}]""", ""","pagination":{"currentPage":2,"totalPages":2,"hasNext":false}"""))
        val r = repo.statement(novPeriod) as NetworkResult.Success
        assertThat(r.data.entries.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
        assertThat(r.data.truncated).isFalse()
        assertThat(server.takeRequest().requestUrl!!.queryParameter("limit")).isEqualTo("200")
        assertThat(server.takeRequest().requestUrl!!.queryParameter("page")).isEqualTo("2")
    }

    @Test fun `service list failure shows every report`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        assertThat(repo.kinds()).containsExactlyElementsIn(ReportKind.entries)
    }
}

/** In-memory repository for view model tests. */
private class FakeRepo : ReportsRepository {
    val pages = ArrayDeque<NetworkResult<ReportPage<ReportRow>>>()
    val ledgerPages = ArrayDeque<NetworkResult<ReportPage<LedgerEntry>>>()
    val queries = mutableListOf<ReportQuery>()
    var complaints = 0
    var complaintResult: NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun ledger(q: LedgerQuery) = ledgerPages.removeFirstOrNull() ?: NetworkResult.Success(ReportPage(emptyList<LedgerEntry>(), null, false), null)
    override suspend fun ledgerEntry(id: Long): NetworkResult<LedgerEntry> = NetworkResult.Failure.NoInternet
    override suspend fun report(kind: ReportKind, q: ReportQuery): NetworkResult<ReportPage<ReportRow>> { queries += q; return pages.removeFirstOrNull() ?: NetworkResult.Success(ReportPage(emptyList(), null, false), null) }
    override suspend fun complain(row: ReportRow, text: String): NetworkResult<Unit> { complaints++; gate?.await(); return complaintResult }
    override suspend fun kinds() = ReportKind.entries.toSet()
    override suspend fun statement(period: Period): NetworkResult<StatementData> = NetworkResult.Success(StatementData(emptyList(), false), null)
}

private fun row(id: String, status: RowStatus = RowStatus.Success) =
    ReportRow(ReportKind.Recharge, id, "JIO", "", BigDecimal.TEN, status, nov6, null, emptyList(), null, "JI")

class ReportsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun vm(repo: FakeRepo) = ReportViewModel(repo, SavedStateHandle(mapOf("kind" to "Recharge")))

    @Test fun `loads, appends the next page and stops at the end`() = runTest {
        val repo = FakeRepo().apply {
            pages += NetworkResult.Success(ReportPage(listOf(row("1"), row("2")), null, true), null)
            pages += NetworkResult.Success(ReportPage(listOf(row("2"), row("3")), null, false), null)
        }
        val vm = vm(repo)
        assertThat(vm.list.value.items.map { it.txnId }).containsExactly("1", "2")
        vm.loadMore()
        assertThat(vm.list.value.items.map { it.txnId }).containsExactly("1", "2", "3").inOrder() // no duplicate
        assertThat(vm.list.value.canLoadMore).isFalse()
        vm.loadMore()
        assertThat(repo.queries).hasSize(2)
        assertThat(repo.queries[1].page).isEqualTo(2)
    }

    @Test fun `a failed refresh keeps the rows already shown`() = runTest {
        val repo = FakeRepo().apply {
            pages += NetworkResult.Success(ReportPage(listOf(row("1")), null, false), null)
            pages += NetworkResult.Failure.NoInternet
        }
        val vm = vm(repo)
        vm.refresh()
        assertThat(vm.list.value.items).hasSize(1)
        assertThat(vm.list.value.error).isEqualTo(UiError.NoInternet)
        assertThat(vm.list.value.loadedAt).isNotNull()
    }

    @Test fun `filters reload from page 1`() = runTest {
        val repo = FakeRepo()
        val vm = vm(repo)
        vm.applyFilters(Periods.of(Preset.LastMonth, nov6), RowStatus.Pending)
        assertThat(repo.queries.last().status).isEqualTo(RowStatus.Pending)
        assertThat(repo.queries.last().page).isEqualTo(1)
        assertThat(repo.queries.last().period.preset).isEqualTo(Preset.LastMonth)
        vm.clearFilters()
        assertThat(repo.queries.last().status).isNull()
    }

    @Test fun `complaint is sent once and shows on the row`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepo().apply { pages += NetworkResult.Success(ReportPage(listOf(row("1")), null, false), null); this.gate = gate }
        val vm = vm(repo)
        vm.open(vm.list.value.items[0])
        vm.startComplaint()
        vm.onComplaintText("Customer didn't receive it")
        vm.sendComplaint(); vm.sendComplaint()
        assertThat(vm.complaint.value).isEqualTo(ComplaintState.Sending)
        gate.complete(Unit)
        assertThat(repo.complaints).isEqualTo(1)
        assertThat(vm.complaint.value).isEqualTo(ComplaintState.Sent)
        assertThat(vm.selected.value!!.complaint!!.text).isEqualTo("Customer didn't receive it")
        assertThat(vm.list.value.items[0].canComplain).isFalse()
    }

    @Test fun `no complaint on a failed row, and a refusal is shown`() = runTest {
        val repo = FakeRepo().apply {
            pages += NetworkResult.Success(ReportPage(listOf(row("1", RowStatus.Failed), row("2")), null, false), null)
            complaintResult = NetworkResult.Failure.Api(400, null, "Complaints can only be filed against SUCCESS or PENDING transactions.")
        }
        val vm = vm(repo)
        vm.open(vm.list.value.items[0]); vm.startComplaint()
        assertThat(vm.complaint.value).isNull()
        vm.open(vm.list.value.items[1]); vm.startComplaint(); vm.onComplaintText("x"); vm.sendComplaint()
        assertThat(vm.complaint.value).isInstanceOf(ComplaintState.Error::class.java)
    }

    @Test fun `ledger type chip reloads`() = runTest {
        val repo = FakeRepo().apply {
            ledgerPages += NetworkResult.Success(ReportPage(emptyList(), null, false), null)
            ledgerPages += NetworkResult.Success(ReportPage(listOf(LedgerEntry(1, "A", nov6, null, BigDecimal.ONE, BigDecimal.ZERO, null, null, null)), null, false), null)
        }
        val vm = LedgerViewModel(repo)
        assertThat(vm.list.value.empty).isTrue()
        vm.setType(LedgerType.MoneyIn)
        assertThat(vm.list.value.items).hasSize(1)
        assertThat(vm.list.value.ledgerType).isEqualTo(LedgerType.MoneyIn)
    }
}

class AddMoneyReportTest {
    private fun obj(s: String) = kotlinx.serialization.json.Json.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject

    @org.junit.Test fun `add money rows - the UAT shape, PayU, pending and failed with a reason`() {
        val ok = com.asl.wallet.feature.reports.data.readRow(com.asl.wallet.feature.reports.domain.ReportKind.AddMoney, obj(
            """{"index":3,"id":"27816","clientTxnId":"PAYU6044_1762768843000","amount":1,"orderId":"ASL6044T1762768843000","customerEmail":"x@y.z","customerMobile":"9798143077",
               "apiTag":"PAYU","pInfo":"Ajay","isPaymentComp":"ONE","status":"SUCCESS","inDate":"10/11/2026","inTime":"3:30:43 pm"}"""))!!
        com.google.common.truth.Truth.assertThat(ok.status).isEqualTo(com.asl.wallet.feature.reports.domain.RowStatus.Success)
        com.google.common.truth.Truth.assertThat(ok.txnId).isEqualTo("PAYU6044_1762768843000")
        com.google.common.truth.Truth.assertThat(ok.amount).isEqualTo(java.math.BigDecimal.ONE)
        com.google.common.truth.Truth.assertThat(ok.day).isEqualTo(com.asl.wallet.feature.reports.domain.Day(2026, 11, 10))
        com.google.common.truth.Truth.assertThat(ok.time).isEqualTo("3:30 PM")
        com.google.common.truth.Truth.assertThat(ok.fields.map { it.second }).doesNotContain("9798143077") // customer details never read
        val failed = com.asl.wallet.feature.reports.data.readRow(com.asl.wallet.feature.reports.domain.ReportKind.AddMoney, obj(
            """{"id":"1","clientTxnId":"PAYU6044_1","amount":500,"status":"FAILED","pInfo":"FAILED: Bank declined","inDate":"09/11/2026","inTime":"1:00:00 pm"}"""))!!
        com.google.common.truth.Truth.assertThat(failed.fields.toMap()[com.asl.wallet.feature.reports.domain.Field.Reason]).isEqualTo("Bank declined")
        val pending = com.asl.wallet.feature.reports.data.readRow(com.asl.wallet.feature.reports.domain.ReportKind.AddMoney, obj(
            """{"id":"2","clientTxnId":"PAYU6044_2","amount":1,"status":"PENDING","pInfo":"FAILED: No Error"}"""))!!
        com.google.common.truth.Truth.assertThat(pending.status).isEqualTo(com.asl.wallet.feature.reports.domain.RowStatus.Pending)
    }
}
