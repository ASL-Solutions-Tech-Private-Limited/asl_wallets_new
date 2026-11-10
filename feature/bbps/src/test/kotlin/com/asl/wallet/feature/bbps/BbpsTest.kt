package com.asl.wallet.feature.bbps

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.bbps.data.BbpsApi
import com.asl.wallet.feature.bbps.data.BbpsRepository
import com.asl.wallet.feature.bbps.data.BbpsRepositoryImpl
import com.asl.wallet.feature.bbps.data.readBill
import com.asl.wallet.feature.bbps.data.readBillerInfo
import com.asl.wallet.feature.bbps.data.readBillers
import com.asl.wallet.feature.bbps.data.readCategories
import com.asl.wallet.feature.bbps.data.readPay
import com.asl.wallet.feature.bbps.domain.BbpsOutcome
import com.asl.wallet.feature.bbps.domain.BbpsReceipt
import com.asl.wallet.feature.bbps.domain.BbpsRules
import com.asl.wallet.feature.bbps.domain.BillCategory
import com.asl.wallet.feature.bbps.domain.BillPayment
import com.asl.wallet.feature.bbps.domain.Biller
import com.asl.wallet.feature.bbps.domain.BillerInfo
import com.asl.wallet.feature.bbps.domain.BillerParam
import com.asl.wallet.feature.bbps.domain.FetchedBill
import com.asl.wallet.feature.bbps.ui.BbpsPage
import com.asl.wallet.feature.bbps.ui.BbpsViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.util.concurrent.TimeUnit

private fun el(s: String): JsonElement = Json.parseToJsonElement(s)

private val wbsedcl = Biller("WEST00000WBL75", "WBSEDCL", "Electricity")
private val consumer = BillerParam("Consumer ID", numeric = true, optional = false, minLength = 9, maxLength = 9, pattern = null)
private val mobile = BillerParam("Mobile Number", numeric = true, optional = false, minLength = 10, maxLength = 10, pattern = Regex("^[6-9][0-9]{9}$"))

/** The old app's fetch-bill answer (asl_new_app BillerBillFetchResponse), wrapped in the new envelope. Test values only. */
private const val FETCH = """{"status":true,"data":{"status":1,"refid":"OINBEPVNFA6J25XA","xml":"PD94bWw=","message":"ok","billData":{
  "responseCode":"000","inputParams":{"input":{"paramName":"Consumer ID","paramValue":"123456789"}},
  "billerResponse":{"billAmount":"52345","billDate":"2026-10-28","billNumber":"B-778","billPeriod":"OCT 2026","customerName":"TEST CUSTOMER","dueDate":"2026-11-15",
    "amountOptions":{"option":[{"amountName":"Late fee","amountValue":"53345"}]}},
  "additionalInfo":{"info":{"infoName":"Balance","infoValue":"0"}}}}}"""

class BbpsReadersTest {
    @Test fun `categories keep active ones, from a plain list or the old success+data shape`() {
        val list = readCategories(el("""{"status":true,"data":[{"name":"Electricity","status":"1"},{"name":"Gas","status":"0"},{"name":"Fastag"}]}"""))
        assertThat(list).containsExactly(BillCategory("Electricity"), BillCategory("Fastag")).inOrder()
        assertThat(readCategories(el("""{"data":{"success":true,"data":[{"name":"Water","category_code":"W"}]}}"""))).containsExactly(BillCategory("Water"))
    }

    @Test fun `billers are read with their code and sorted`() {
        val b = readBillers(el("""{"data":[{"name":"WBSEDCL","operator_code":"WEST00000WBL75"},{"billerName":"CESC","billerId":"CESC00000KOL01"},{"name":"no code"}]}"""), "Electricity")
        assertThat(b.map { it.name }).containsExactly("CESC", "WBSEDCL").inOrder()
        assertThat(b.first().category).isEqualTo("Electricity")
    }

    @Test fun `biller inputs come as one object or a list, hidden ones are skipped and a bad pattern is ignored`() {
        val one = readBillerInfo(el("""{"data":{"mdmRequestNew":{"biller":{"billerInputParams":{"paramInfo":
            {"paramName":"Consumer ID","dataType":"NUMERIC","isOptional":"false","minLength":"9","maxLength":"9"}}}}}}"""), wbsedcl)!!
        assertThat(one.params).containsExactly(consumer)
        val many = readBillerInfo(el("""{"data":{"refid":"x","mdmRequestNew":{"biller":{"billerInputParams":{"paramInfo":[
            {"paramName":"Vehicle Number","dataType":"ALPHANUMERIC","isOptional":"false","regEx":"^[A-Z0-9]{6,12}$"},
            {"paramName":"Internal","visibility":"false"},
            {"paramName":"Area","dataType":"ALPHANUMERIC","isOptional":"true","regEx":"([","values":"North,South"}]}}}}}"""), wbsedcl)!!
        assertThat(many.params.map { it.name }).containsExactly("Vehicle Number", "Area").inOrder()
        assertThat(many.params[1].pattern).isNull()
        assertThat(many.params[1].choices).containsExactly("North", "South")
    }

    @Test fun `a fetched bill is in paise, keeps refid and xml for payment, and needs both`() {
        val b = readBill(el(FETCH))!!
        assertThat(b.amount).isEqualTo(BigDecimal("523.45"))
        assertThat(b.requestId).isEqualTo("OINBEPVNFA6J25XA")
        assertThat(b.xml).isEqualTo("PD94bWw=")
        assertThat(b.customerName).isEqualTo("TEST CUSTOMER")
        assertThat(b.options).containsExactly("Late fee" to BigDecimal("533.45"))
        assertThat(b.details).containsExactly("Balance" to "0")
        assertThat(readBill(el(FETCH.replace("\"xml\":\"PD94bWw=\",", "")))).isNull()
    }

    @Test fun `payment answer is read from the old check-status shape`() {
        val a = readPay(el("""{"status":true,"data":{"status":1,"apiTrnID":"CC015255CBAA41271554","txnstatus":"Success","message":"Success","ref_id":"212INH48","updated_user_balance":47166.586}}"""))
        assertThat(a.status).isEqualTo("Success")
        assertThat(a.bbpsTxnId).isEqualTo("CC015255CBAA41271554")
        assertThat(a.refId).isEqualTo("212INH48")
        assertThat(a.balanceAfter).isEqualTo(BigDecimal("47166.58"))
    }

    @Test fun `inputs are checked against the biller's own rules`() {
        assertThat(consumer.check("")).isEqualTo(BillerParam.Check.Missing)
        assertThat(consumer.check("1234")).isEqualTo(BillerParam.Check.TooShort)
        assertThat(consumer.check("12345678A")).isEqualTo(BillerParam.Check.Invalid)
        assertThat(consumer.check("123456789")).isEqualTo(BillerParam.Check.Ok)
        assertThat(mobile.check("5123456789")).isEqualTo(BillerParam.Check.Invalid)
        assertThat(consumer.copy(optional = true).check("")).isEqualTo(BillerParam.Check.Ok)
    }

    @Test fun `amount rules - editable only for prepaid-style categories or when the biller sends none`() {
        assertThat(BbpsRules.amountEditable("Electricity", BigDecimal("523.45"))).isFalse()
        assertThat(BbpsRules.amountEditable("Electricity", BigDecimal.ZERO)).isTrue()
        assertThat(BbpsRules.amountEditable("Fastag", BigDecimal("100"))).isTrue()
        assertThat(BbpsRules.amountOf("523.45")).isEqualTo(BigDecimal("523.45"))
        assertThat(BbpsRules.amountOf("523.456")).isNull()
        assertThat(BbpsRules.amountOf("0")).isNull()
        assertThat(BbpsRules.statusOf("in progress")).isEqualTo(BbpsRules.Status.Pending)
    }
}

class BbpsRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: BbpsRepositoryImpl
    private val bill = readBill(el(FETCH))!!

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = BbpsRepositoryImpl(retrofit.create(BbpsApi::class.java), json)
    }
    @After fun tearDown() = server.shutdown()
    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    private fun payment(key: IdempotencyKey = IdempotencyKey("k-1")) =
        BillPayment(wbsedcl, listOf(consumer to "123456789", mobile to "9876543210"), bill, BigDecimal("523.45"), key)

    @Test fun `fetch sends the biller's field names as postdata`() = runTest {
        respond(200, FETCH)
        val r = repo.fetchBill(wbsedcl, listOf(consumer to "123456789", mobile to "9876543210"))
        assertThat((r as NetworkResult.Success).data.requestId).isEqualTo("OINBEPVNFA6J25XA")
        val body = el(server.takeRequest().body.readUtf8()).jsonObject
        assertThat(body["operator_code"]!!.jsonPrimitive.content).isEqualTo("WEST00000WBL75")
        assertThat(body["postdata"]!!.jsonObject["Consumer ID"]!!.jsonPrimitive.content).isEqualTo("123456789")
    }

    @Test fun `a refusal inside a 200 is a failure with the server's message`() = runTest {
        respond(200, """{"status":true,"data":{"success":false,"message":"Invalid Consumer ID"}}""")
        val r = repo.fetchBill(wbsedcl, listOf(consumer to "123456789"))
        assertThat((r as NetworkResult.Failure.Api).message).isEqualTo("Invalid Consumer ID")
    }

    @Test fun `pay sends the fetched refid and xml, the amount and one idempotency key`() = runTest {
        respond(200, """{"status":true,"data":{"status":1,"txnstatus":"Success","ref_id":"R1","apiTrnID":"CC01","updated_user_balance":"100.5"}}""")
        val o = repo.pay(payment())
        assertThat(o).isInstanceOf(BbpsOutcome.Paid::class.java)
        assertThat((o as BbpsOutcome.Paid).balanceAfter).isEqualTo(BigDecimal("100.50"))
        assertThat(o.receipt.refId).isEqualTo("R1")
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo("k-1")
        val body = el(req.body.readUtf8()).jsonObject
        assertThat(body["requestId"]!!.jsonPrimitive.content).isEqualTo("OINBEPVNFA6J25XA")
        assertThat(body["billPaymentRequestxml"]!!.jsonPrimitive.content).isEqualTo("PD94bWw=")
        assertThat(body["amount"]!!.jsonPrimitive.content).isEqualTo("523.45")
        assertThat(body["ca_number"]!!.jsonPrimitive.content).isEqualTo("123456789")
        assertThat(body["Mobile_Number"]!!.jsonPrimitive.content).isEqualTo("9876543210")
        assertThat(body["operator"]!!.jsonPrimitive.content).isEqualTo("WEST00000WBL75")
    }

    @Test fun `unclear answers, 5xx and timeouts are Pending - never success by guess, never retried`() = runTest {
        respond(200, """{"status":true,"data":{"status":1,"txnstatus":"Initiated","ref_id":"R2"}}""")
        assertThat(repo.pay(payment())).isInstanceOf(BbpsOutcome.Pending::class.java)
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.pay(payment())).isInstanceOf(BbpsOutcome.Pending::class.java)
        server.enqueue(MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("{}"))
        assertThat(repo.pay(payment())).isInstanceOf(BbpsOutcome.Pending::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test fun `biller decline is Failed and returned, a 4xx refusal is Failed and not taken`() = runTest {
        respond(200, """{"status":true,"data":{"status":0,"txnstatus":"Failure","message":"Bill already paid"}}""")
        val f = repo.pay(payment()) as BbpsOutcome.Failed
        assertThat(f.debited).isTrue()
        assertThat(f.reason).isEqualTo("Bill already paid")
        respond(422, """{"status":false,"message":"Insufficient wallet balance"}""")
        val g = repo.pay(payment()) as BbpsOutcome.Failed
        assertThat(g.debited).isFalse()
    }
}

class BbpsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeRepo : BbpsRepository {
        var balance = BigDecimal("1000")
        var payCalls = 0
        val bill = FetchedBill("REF", "XML", "TEST CUSTOMER", "B-1", null, null, null, BigDecimal("523.45"))
        override suspend fun categories() = NetworkResult.Success(listOf(BillCategory("Electricity"), BillCategory("Fastag")), null)
        override suspend fun billers(category: String) = NetworkResult.Success(listOf(wbsedcl, Biller("CESC1", "CESC", category)), null)
        override suspend fun billerInfo(biller: Biller) = NetworkResult.Success(BillerInfo(biller, listOf(consumer)), null)
        override suspend fun fetchBill(biller: Biller, inputs: List<Pair<BillerParam, String>>) = NetworkResult.Success(bill, null)
        override suspend fun pay(p: com.asl.wallet.feature.bbps.domain.BillPayment): BbpsOutcome {
            payCalls++
            return BbpsOutcome.Paid(BbpsReceipt(p.biller.category, p.biller.name, "Consumer ID", p.consumerNumber, null, null, p.amount, "R", "C", ""), null)
        }
        override suspend fun balance() = NetworkResult.Success(balance, null)
    }

    @Test fun `a Home tile opens its category and Back from there leaves BBPS`() = runTest {
        val vm = BbpsViewModel(FakeRepo())
        vm.start("Electricity"); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(BbpsPage.Billers)
        vm.onQuery("ces")
        assertThat(vm.state.value.shownBillers.map { it.name }).containsExactly("CESC")
        assertThat(vm.back()).isFalse()
    }

    @Test fun `details are checked before fetching, then the bill pays once after confirming`() = runTest {
        val repo = FakeRepo()
        val vm = BbpsViewModel(repo)
        vm.start(null); advanceUntilIdle()
        assertThat(vm.state.value.categories).hasSize(2)
        vm.openCategory("Electricity"); advanceUntilIdle()
        vm.openBiller(wbsedcl); advanceUntilIdle()
        vm.fetch(); advanceUntilIdle()
        assertThat(vm.state.value.showErrors).isTrue()
        assertThat(vm.state.value.page).isEqualTo(BbpsPage.Details)
        vm.onValue(consumer, "12345678 9x"); // digits only, 9 at most
        assertThat(vm.state.value.values["Consumer ID"]).isEqualTo("123456789")
        vm.fetch(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(BbpsPage.Bill)
        assertThat(vm.state.value.amountText).isEqualTo("523.45")
        assertThat(vm.state.value.amountEditable).isFalse()
        vm.onAmount("1") // electricity amount is fixed by the biller
        assertThat(vm.state.value.amountText).isEqualTo("523.45")
        vm.pay(); advanceUntilIdle()
        assertThat(repo.payCalls).isEqualTo(0) // never without confirming
        vm.askPay()
        assertThat(vm.state.value.confirm).isTrue()
        vm.pay(); vm.pay(); advanceUntilIdle()
        assertThat(repo.payCalls).isEqualTo(1)
        assertThat(vm.state.value.page).isEqualTo(BbpsPage.Result)
        assertThat(vm.state.value.outcome!!.receipt.time).isNotEmpty()
    }

    @Test fun `low balance asks to add money instead of paying`() = runTest {
        val repo = FakeRepo().apply { balance = BigDecimal("100") }
        val vm = BbpsViewModel(repo)
        vm.start("Electricity"); advanceUntilIdle()
        vm.openBiller(wbsedcl); advanceUntilIdle()
        vm.onValue(consumer, "123456789"); vm.fetch(); advanceUntilIdle()
        vm.askPay()
        assertThat(vm.state.value.lowBalance).isTrue()
        assertThat(vm.state.value.confirm).isFalse()
    }
}
