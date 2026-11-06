package com.asl.wallet.feature.recharge

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.recharge.data.RechargeApi
import com.asl.wallet.feature.recharge.data.RechargeInput
import com.asl.wallet.feature.recharge.data.RechargeRepository
import com.asl.wallet.feature.recharge.data.RechargeRepositoryImpl
import com.asl.wallet.feature.recharge.data.ReportRowDto
import com.asl.wallet.feature.recharge.data.readDthAnswer
import com.asl.wallet.feature.recharge.data.readDthCustomer
import com.asl.wallet.feature.recharge.data.readOperators
import com.asl.wallet.feature.recharge.data.readPlans
import com.asl.wallet.feature.recharge.data.shortOperator
import com.asl.wallet.feature.recharge.data.toRecent
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
import com.asl.wallet.feature.recharge.ui.Detect
import com.asl.wallet.feature.recharge.ui.DthInfo
import com.asl.wallet.feature.recharge.ui.Page
import com.asl.wallet.feature.recharge.ui.RechargeConfig
import com.asl.wallet.feature.recharge.ui.RechargeViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class RechargeRulesTest {
    @Test fun `rtype is 1 for mobile and 2 for DTH`() {
        assertThat(RechargeType.Mobile.rtype).isEqualTo(1)
        assertThat(RechargeType.Dth.rtype).isEqualTo(2)
    }

    @Test fun `numbers - mobile 10 digits from 6 to 9, contacts cleaned, DTH IDs 6 to 20`() {
        assertThat(RechargeRules.isNumberOk(RechargeType.Mobile, "9798143077")).isTrue()
        assertThat(RechargeRules.isNumberOk(RechargeType.Mobile, "4598743077")).isFalse()
        assertThat(RechargeRules.cleanMobile("+91 97981-43077")).isEqualTo("9798143077")
        assertThat(RechargeRules.cleanMobile("09798143077")).isEqualTo("9798143077")
        assertThat(RechargeRules.isNumberOk(RechargeType.Dth, "1465946273")).isTrue()
        assertThat(RechargeRules.isNumberOk(RechargeType.Dth, "12345")).isFalse()
        assertThat(RechargeRules.isNumberOk(RechargeType.Dth, "123456789")).isFalse() // server minimum is 10 (UAT 5 Nov)
        assertThat(RechargeRules.isNumberOk(RechargeType.Dth, "01234567890")).isTrue()
        assertThat(RechargeRules.isNumberOk(RechargeType.Dth, "1465 9462")).isFalse()
    }

    @Test fun `amount is whole rupees within sane bounds`() {
        assertThat(RechargeRules.amountOf("349")).isEqualTo(349)
        assertThat(RechargeRules.amountOf("0")).isNull()
        assertThat(RechargeRules.amountOf("49.5")).isNull()
        assertThat(RechargeRules.amountOf("50001")).isNull()
    }

    @Test fun `status - only clear success is success, unknown is pending`() {
        assertThat(RechargeStatuses.of("SUCCESS")).isEqualTo(RechargeStatus.Success)
        assertThat(RechargeStatuses.of("FAILURE")).isEqualTo(RechargeStatus.Failed)
        assertThat(RechargeStatuses.of("LOGIN FAILED")).isEqualTo(RechargeStatus.Failed)
        assertThat(RechargeStatuses.of("PENDING")).isEqualTo(RechargeStatus.Pending)
        assertThat(RechargeStatuses.of(null)).isEqualTo(RechargeStatus.Pending)
        assertThat(RechargeStatuses.of("ACCEPTED")).isEqualTo(RechargeStatus.Pending)
    }

    @Test fun `plan text loses links, validity reads well, search by amount or words`() {
        assertThat(RechargeRules.cleanText("Benefits:\n 1. JioTV\n3. click on: http://tiny.jio.com/JioTVHome")).isEqualTo("Benefits:\n1. JioTV\n3. click on:")
        assertThat(RechargeRules.cleanValidity("28Days")).isEqualTo("28 days")
        assertThat(RechargeRules.cleanValidity("NA")).isEqualTo("NA")
        val plans = listOf(Plan(349, "28 days", "2 GB/day, Jio apps", "True 5G"), Plan(399, "28 days", "JioHotstar mobile, 2.5 GB/day", "True 5G"), Plan(19, "1 days", "1 GB data", "DATA"))
        assertThat(RechargeRules.search(plans, "3").map { it.amount }).containsExactly(349, 399)
        assertThat(RechargeRules.search(plans, "hotstar").map { it.amount }).containsExactly(399)
        assertThat(RechargeRules.search(plans, "data").map { it.amount }).containsExactly(19)
    }

    @Test fun `plans are grouped by their readable name, same names joined, cheapest first`() {
        val g = RechargeRules.group(listOf(
            Plan(399, "28 days", "a", "True 5G Unlimited Plans"), Plan(349, "28 days", "b", "True 5G Unlimited Plans"), Plan(349, "28 days", "b", "True 5G Unlimited"),
            Plan(19, "1 days", "c", "DATA"),
        ))
        assertThat(g.map { it.name }).containsExactly("True 5G Unlimited Plans", "DATA").inOrder()
        assertThat(g[0].plans.map { it.amount }).containsExactly(349, 399).inOrder()
    }
}

class RechargeMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `UAT plan shape - data map of categories with type, amount, validity, description`() {
        val o = json.parseToJsonElement("""{"status":"success","operator":"RELIANCE JIO","circle":"West Bengal","data":{
            "FULLTT":[{"type":"FULLTT Plans","amount":55,"validity":"3Days","description":"3 GB\nhttp://x.y","last_updated":"x"}],
            "TrueUnlimitedUpgrade":[{"type":"True 5G Unlimited Plans","amount":349,"validity":"28Days","description":"2GB/Day"}],
            "True Unlimited Upgrade":[{"type":"True 5G Unlimited Plans","amount":299,"validity":"28Days","description":"1.5GB/Day"}],
            "Broken":[{"type":"X","amount":"abc"},{"type":"X","amount":0}]}}""").jsonObject
        val groups = readPlans(o)
        assertThat(groups.map { it.name }).containsExactly("FULLTT Plans", "True 5G Unlimited Plans").inOrder()
        assertThat(groups[1].plans.map { it.amount }).containsExactly(299, 349).inOrder()
        assertThat(groups[0].plans[0].description).isEqualTo("3 GB")
        assertThat(groups[0].plans[0].validity).isEqualTo("3 days")
    }

    @Test fun `operator names are short and the undocumented list keys are all read`() {
        assertThat(shortOperator("Reliance Jio Infocomm Limited")).isEqualTo("Jio")
        assertThat(shortOperator("Vodafone Idea")).isEqualTo("Vi")
        val ops = readOperators(json.parseToJsonElement("""[{"optcode":"56","operator_name":"JIO"},{"id":73,"name":"Tata Play"},{"name":"no code"}]"""))
        assertThat(ops).containsExactly(Operator("56", "Jio"), Operator("73", "Tata Play")).inOrder()
        // UAT shape on 5 Nov: optcode as a number, optname in capitals; Vodafone and Idea stay separate.
        val uat = readOperators(json.parseToJsonElement("""[{"optcode":39,"optname":"VODAFONE","opt_icon":"https://x/1.png"},{"optcode":58,"optname":"IDEA"},
            {"optcode":64,"optname":"BSNL TOPUP"},{"optcode":74,"optname":"VIDEOCON D2H"},{"optcode":75,"optname":"AIRTEL DIGITAL TV"}]"""))
        assertThat(uat.map { it.code to it.name }).containsExactly("39" to "Vodafone", "58" to "Idea", "64" to "BSNL Topup", "74" to "Videocon D2H", "75" to "Airtel Digital TV").inOrder()
        assertThat(readOperators(null)).isEmpty()
    }

    @Test fun `report rows become recent recharges, DTH by number shape`() {
        val m = ReportRowDto(rechargeNo = "9798143077", operatorName = "JIO", operatorCode = JsonPrimitive("56"), amount = JsonPrimitive(19), tranStatus = "FAILURE", trandate = "04-11-2026").toRecent()!!
        assertThat(m.type).isEqualTo(RechargeType.Mobile)
        assertThat(m.status).isEqualTo(RechargeStatus.Failed)
        assertThat(ReportRowDto(rechargeNo = "1465946273", amount = JsonPrimitive(300)).toRecent()!!.type).isEqualTo(RechargeType.Dth)
        assertThat(ReportRowDto(rechargeNo = null, amount = JsonPrimitive(1)).toRecent()).isNull()
    }

    @Test fun `UAT 5 Nov DTH customer-info answers - down, bad ID with reason, found in DATA or records`() {
        val down = readDthAnswer(json.parseToJsonElement("""{"error":"3","DATA":null,"Message":"DOWN"}""").jsonObject) as NetworkResult.Failure.Api
        assertThat(down.code).isEqualTo("DTH_DOWN")
        val bad = readDthAnswer(json.parseToJsonElement("""{"error":"1","DATA":null,"Message":"Customer Id Starts with 0 and is 11 digits long"}""").jsonObject) as NetworkResult.Failure.Api
        assertThat(bad.code).isEqualTo("DTH_ID")
        assertThat(bad.message).isEqualTo("Customer Id Starts with 0 and is 11 digits long")
        val found = readDthAnswer(json.parseToJsonElement("""{"error":"0","Message":"OK","DATA":{"records":[{"CustomerName":"Rahul Kumar","planname":"Hindi Smart Saver",
            "status":"Active","Balance":"12.40","MonthlyRecharge":"300","NextRechargeDate":"2026-11-08"}]}}""").jsonObject) as NetworkResult.Success
        assertThat(found.data!!.name).isEqualTo("Rahul Kumar")
        assertThat(found.data!!.monthly).isEqualTo(BigDecimal("300"))
        assertThat(found.data!!.nextRecharge).isEqualTo("2026-11-08")
        val flat = readDthAnswer(json.parseToJsonElement("""{"error":"0","DATA":[{"customer_name":"Sita","monthly_recharge_amount":"250"}]}""").jsonObject) as NetworkResult.Success
        assertThat(flat.data!!.name).isEqualTo("Sita")
        assertThat((readDthAnswer(json.parseToJsonElement("""{"error":"0","DATA":null}""").jsonObject) as NetworkResult.Success).data).isNull()
    }

    @Test fun `DTH customer details read leniently, empty answer is not found`() {
        val c = readDthCustomer(json.parseToJsonElement("""{"customerName":"Rahul","packName":"Hindi Smart","monthlyAmount":"300.00","balance":12.4}""").jsonObject)!!
        assertThat(c.monthly).isEqualTo(BigDecimal("300.00"))
        assertThat(readDthCustomer(JsonObject(emptyMap()))).isNull()
    }
}

class RechargeRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: RechargeRepositoryImpl
    private val jio = Operator("56", "Jio")

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = RechargeRepositoryImpl(retrofit.create(RechargeApi::class.java), json)
    }
    @After fun tearDown() = server.shutdown()
    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    private fun input(key: IdempotencyKey = IdempotencyKey.new(), type: RechargeType = RechargeType.Mobile, number: String = "9798143077") =
        RechargeInput(type, number, jio, 349, "Rahul", "28 days · ₹ 349", key)

    @Test fun `operator detection reads the UAT shape and sends Mobileno`() = runTest {
        respond(200, """{"status":true,"data":{"ERROR":"0","STATUS":"1","Mobile":"919798143077","Operator":"Reliance Jio Infocomm Limited","OpCode":56,"Circle":"Bihar and Jharkhand","CircleCode":"52"}}""")
        val d = (repo.detect("9798143077") as NetworkResult.Success).data!!
        assertThat(d).isEqualTo(DetectedOperator(Operator("56", "Jio"), "Bihar and Jharkhand"))
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"Mobileno":"9798143077"}""")
        respond(200, """{"status":true,"data":{"ERROR":"1","Message":"Not found"}}""")
        assertThat((repo.detect("9798143077") as NetworkResult.Success).data).isNull()
    }

    @Test fun `empty operator lists (UAT today) are not cached, so a retry asks again`() = runTest {
        respond(200, """{"status":true,"data":{"mobile_prepaid":[],"dth_recharge":[]}}""")
        respond(200, """{"status":true,"data":{"mobile_prepaid":[],"dth_recharge":[{"optcode":"73","operator_name":"Tata Play"}]}}""")
        assertThat((repo.operators() as NetworkResult.Success).data.dth).isEmpty()
        assertThat((repo.operators() as NetworkResult.Success).data.dth).containsExactly(Operator("73", "Tata Play"))
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `recharge sends rtype, operator and amount with the Idempotency-Key header`() = runTest {
        respond(200, """{"status":true,"data":{"servid":"RCH63","tranStatus":"SUCCESS","operatorRefNo":"BR000A1"}}""")
        val key = IdempotencyKey.new()
        val o = repo.recharge(input(key)) as RechargeOutcome.Done
        assertThat(o.receipt.txnId).isEqualTo("RCH63")
        assertThat(o.receipt.operatorRef).isEqualTo("BR000A1")
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo(key.value)
        assertThat(req.body.readUtf8()).isEqualTo("""{"custmob":"9798143077","rechopt":"56","rechamt":349,"rtype":1,"conname":"Rahul"}""")
    }

    @Test fun `DTH recharge uses rtype 2 and the subscriber ID`() = runTest {
        respond(200, """{"status":true,"data":{"tranStatus":"PENDING"}}""")
        assertThat(repo.recharge(input(type = RechargeType.Dth, number = "1465946273"))).isInstanceOf(RechargeOutcome.Pending::class.java)
        assertThat(server.takeRequest().body.readUtf8()).contains("\"custmob\":\"1465946273\",\"rechopt\":\"56\",\"rechamt\":349,\"rtype\":2")
    }

    @Test fun `timeout or 5xx is Pending, a 4xx or FAILURE is Failed with a safe message`() = runTest {
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.recharge(input())).isInstanceOf(RechargeOutcome.Pending::class.java)
        respond(400, """{"status":false,"message":"Insufficient wallet balance"}""")
        val refused = repo.recharge(input()) as RechargeOutcome.Failed
        assertThat(refused.reason).isEqualTo("Insufficient wallet balance")
        assertThat(refused.debited).isFalse() // a 4xx never took money
        respond(200, """{"status":true,"data":{"tranStatus":"FAILURE","tranMsg":"Invalid `prisma` column"}}""")
        assertThat((repo.recharge(input()) as RechargeOutcome.Failed).reason).isNull() // internal text never reaches the screen
        respond(200, """{"status":true,"data":null}""")
        assertThat(repo.recharge(input())).isInstanceOf(RechargeOutcome.Failed::class.java) // empty success body: not counted as done
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThat(repo.recharge(input())).isInstanceOf(RechargeOutcome.Pending::class.java)
    }

    @Test fun `DTH info sends consumer_id and operator_code`() = runTest {
        respond(200, """{"status":true,"message":"Customer info fetched successfully","data":{"error":"3","DATA":null,"Message":"DOWN"}}""")
        assertThat((repo.dthInfo(Operator("73", "Tata Sky"), "1465946273") as NetworkResult.Failure.Api).code).isEqualTo("DTH_DOWN")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"consumer_id":"1465946273","operator_code":"73"}""")
    }

    @Test fun `plans are cached per operator`() = runTest {
        respond(200, """{"status":true,"data":{"data":{"DATA":[{"type":"DATA","amount":19,"validity":"1Days","description":"1GB"}]}}}""")
        repeat(2) { assertThat((repo.plans(jio) as NetworkResult.Success).data).hasSize(1) }
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"optcode":"56"}""")
    }
}

// ---------------------------------------------------------------- ViewModel

private class FakeRepo : RechargeRepository {
    var lists = OperatorLists(emptyList(), emptyList())
    var detected: NetworkResult<DetectedOperator?> = NetworkResult.Success(DetectedOperator(Operator("56", "Jio"), "Bihar and Jharkhand"), null)
    var balance: NetworkResult<BigDecimal> = NetworkResult.Success(BigDecimal(1023), null)
    var outcome: (RechargeInput) -> RechargeOutcome = { RechargeOutcome.Pending(receipt(it)) }
    val sent = mutableListOf<RechargeInput>()
    var dthCalls = 0

    fun receipt(i: RechargeInput) = RechargeReceipt(i.type, i.number, i.operator.name, BigDecimal(i.amount), null, null, null, null, "")
    override suspend fun operators() = NetworkResult.Success(lists, null)
    override suspend fun detect(mobile: String) = detected
    override suspend fun plans(operator: Operator): NetworkResult<List<PlanGroup>> = NetworkResult.Success(listOf(PlanGroup("Unlimited", listOf(Plan(349, "28 days", "2GB/day", "Unlimited")))), null)
    var dthResult: NetworkResult<DthCustomer?> = NetworkResult.Success(null, null)
    override suspend fun dthInfo(operator: Operator, subscriberId: String): NetworkResult<DthCustomer?> { dthCalls++; return dthResult }
    override suspend fun recharge(input: RechargeInput): RechargeOutcome { sent += input; return outcome(input) }
    override suspend fun recent(): NetworkResult<List<RecentRecharge>> = NetworkResult.Success(emptyList(), null)
    override suspend fun balance() = balance
}

class RechargeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `10 digits find the operator, a plan fills the amount, typing another clears the plan`() = runTest {
        val vm = RechargeViewModel(FakeRepo(), RechargeConfig(dthInfoEnabled = false))
        vm.onNumber("97981 43077"); advanceUntilIdle()
        assertThat(vm.state.value.detect).isEqualTo(Detect.Found("Bihar and Jharkhand"))
        assertThat(vm.state.value.operator?.code).isEqualTo("56")
        vm.openPlans(); advanceUntilIdle()
        vm.pickPlan(vm.state.value.visiblePlans.first())
        assertThat(vm.state.value.amountText).isEqualTo("349")
        assertThat(vm.state.value.page).isEqualTo(Page.Form)
        vm.onAmount("399")
        assertThat(vm.state.value.plan).isNull()
    }

    @Test fun `slide sends exactly one recharge with one key, a second slide does nothing`() = runTest {
        val repo = FakeRepo()
        val vm = RechargeViewModel(repo, RechargeConfig(false))
        vm.onNumber("9798143077"); advanceUntilIdle(); vm.onAmount("349")
        vm.review(); advanceUntilIdle()
        vm.confirm(); vm.confirm(); advanceUntilIdle()
        assertThat(repo.sent).hasSize(1)
        assertThat(vm.state.value.page).isEqualTo(Page.Result)
        assertThat(repo.sent[0].type.rtype).isEqualTo(1)
        vm.again(keepDetails = true); vm.review(); advanceUntilIdle(); vm.confirm(); advanceUntilIdle()
        assertThat(repo.sent).hasSize(2)
        assertThat(repo.sent[1].key).isNotEqualTo(repo.sent[0].key)
    }

    @Test fun `not enough balance never recharges`() = runTest {
        val repo = FakeRepo().apply { balance = NetworkResult.Success(BigDecimal(100), null) }
        val vm = RechargeViewModel(repo, RechargeConfig(false))
        vm.onNumber("9798143077"); advanceUntilIdle(); vm.onAmount("349")
        vm.review(); advanceUntilIdle()
        assertThat(vm.state.value.shortBy).isEqualTo(BigDecimal(249))
        vm.confirm(); advanceUntilIdle()
        assertThat(repo.sent).isEmpty()
    }

    @Test fun `review needs a valid number, operator and amount`() = runTest {
        val repo = FakeRepo().apply { detected = NetworkResult.Success(null, null) }
        val vm = RechargeViewModel(repo, RechargeConfig(false))
        vm.onNumber("9798143077"); advanceUntilIdle(); vm.onAmount("349")
        assertThat(vm.state.value.detect).isEqualTo(Detect.NotFound)
        vm.review()
        assertThat(vm.state.value.page).isEqualTo(Page.Form)
        assertThat(vm.state.value.showErrors).isTrue()
        vm.chooseOperator(Operator("1", "Airtel")); vm.review(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(Page.Review)
    }

    @Test fun `DTH - empty operator list shows unavailable, details stay off until the API exists`() = runTest {
        val repo = FakeRepo()
        val vm = RechargeViewModel(repo, RechargeConfig(dthInfoEnabled = false))
        vm.start(RechargeType.Dth); advanceUntilIdle()
        assertThat(vm.state.value.dthAvailable).isFalse()
        repo.lists = OperatorLists(emptyList(), listOf(Operator("73", "Tata Play")))
        val vm2 = RechargeViewModel(repo, RechargeConfig(dthInfoEnabled = false))
        vm2.start(RechargeType.Dth); advanceUntilIdle()
        vm2.chooseOperator(Operator("73", "Tata Play")); vm2.onNumber("1465946273"); vm2.getDthDetails()
        assertThat(vm2.state.value.dth).isEqualTo(DthInfo.Soon)
        assertThat(repo.dthCalls).isEqualTo(0)
        val vm3 = RechargeViewModel(repo, RechargeConfig(dthInfoEnabled = true))
        vm3.start(RechargeType.Dth); advanceUntilIdle()
        vm3.chooseOperator(Operator("73", "Tata Play")); vm3.onNumber("1465946273"); vm3.getDthDetails(); advanceUntilIdle()
        assertThat(vm3.state.value.dth).isEqualTo(DthInfo.NotFound(null))
        vm3.onAmount("300"); vm3.review(); advanceUntilIdle(); vm3.confirm(); advanceUntilIdle()
        assertThat(repo.sent.last().type.rtype).isEqualTo(2)

        // Provider down or a bad ID: the retailer is told, and can still recharge.
        repo.dthResult = NetworkResult.Failure.Api(503, "DTH_DOWN", "DOWN")
        val vm4 = RechargeViewModel(repo, RechargeConfig(dthInfoEnabled = true))
        vm4.start(RechargeType.Dth); advanceUntilIdle(); vm4.chooseOperator(Operator("73", "Tata Sky")); vm4.onNumber("1465946273"); vm4.getDthDetails(); advanceUntilIdle()
        assertThat(vm4.state.value.dth).isEqualTo(DthInfo.Unavailable)
        repo.dthResult = NetworkResult.Failure.Api(422, "DTH_ID", "Customer Id Starts with 0 and is 11 digits long")
        vm4.onNumber("1465946274"); vm4.getDthDetails(); advanceUntilIdle()
        assertThat(vm4.state.value.dth).isEqualTo(DthInfo.NotFound("Customer Id Starts with 0 and is 11 digits long"))
    }
}
