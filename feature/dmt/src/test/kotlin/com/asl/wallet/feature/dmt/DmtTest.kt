package com.asl.wallet.feature.dmt

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.security.storage.StringCipher
import com.asl.wallet.feature.dmt.data.DmtApi
import com.asl.wallet.feature.dmt.data.EncryptedRecentCustomerStore
import com.asl.wallet.feature.dmt.data.RecentCustomer
import com.asl.wallet.feature.dmt.data.RecentCustomerStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.asl.wallet.feature.dmt.data.DmtRepository
import com.asl.wallet.feature.dmt.data.DmtRepositoryImpl
import com.asl.wallet.feature.dmt.data.TransferInput
import com.asl.wallet.feature.dmt.data.readCharges
import com.asl.wallet.feature.dmt.data.readLookup
import com.asl.wallet.feature.dmt.data.readName
import com.asl.wallet.feature.dmt.data.readRecipients
import com.asl.wallet.feature.dmt.data.readTransfer
import com.asl.wallet.feature.dmt.domain.Charges
import com.asl.wallet.feature.dmt.domain.Customer
import com.asl.wallet.feature.dmt.domain.CustomerLookup
import com.asl.wallet.feature.dmt.domain.DmtBank
import com.asl.wallet.feature.dmt.domain.DmtOutcome
import com.asl.wallet.feature.dmt.domain.DmtReceipt
import com.asl.wallet.feature.dmt.domain.DmtRules
import com.asl.wallet.feature.dmt.domain.Gender
import com.asl.wallet.feature.dmt.domain.OtpPurpose
import com.asl.wallet.feature.dmt.domain.Recipient
import com.asl.wallet.feature.dmt.domain.RecipientForm
import com.asl.wallet.feature.dmt.domain.TransferMode
import com.asl.wallet.feature.dmt.ui.DmtPage
import com.asl.wallet.feature.dmt.ui.DmtViewModel
import com.asl.wallet.feature.dmt.ui.NameCheck
import com.asl.wallet.feature.dmt.ui.OtpFor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
private fun el(s: String) = json.parseToJsonElement(s)
private val customer = Customer("9876543210", "Rahul Kumar", BigDecimal(25000), BigDecimal(5500))
private val nikhil = Recipient("1654", "NIKHIL KUMAR", "UCO Bank", "32840110063874", "UCBA0003284", "9123456780", true)

class DmtRulesTest {
    @Test fun `customer lookup reads statusCheck 0, 1 and 2, beside or inside data`() {
        assertThat(readLookup("9876543210", el("""{"statusCheck":"0"}"""))).isEqualTo(CustomerLookup.NotRegistered)
        assertThat(readLookup("9876543210", el("""{"statusCheck":"1","data":{"cust_name":"Rahul"}}"""))).isEqualTo(CustomerLookup.NeedsOtp)
        val found = readLookup("9876543210", el("""{"statusCheck":"2","data":{"cust_name":"Rahul Kumar","cust_mob":"9876543210","totalLimit":25000,"consumedLimit":"5500"}}""")) as CustomerLookup.Found
        assertThat(found.customer).isEqualTo(customer)
        assertThat(found.customer.leftLimit).isEqualTo(BigDecimal(19500))
        assertThat(readLookup("9876543210", el("""{"cust_name":"Asha","statusCheck":2}"""))).isInstanceOf(CustomerLookup.Found::class.java)
    }

    @Test fun `UAT 5 Nov lookup shapes - found in data_data, not exist, verify your OTP`() {
        val found = readLookup("9876543210", el("""{"status":true,"message":"Remitter verified successfully","data":{"id":"1433","cust_id":"1433","cust_name":"Nikhil K Gupta",
            "cust_mob":"••••5218","dmt_use_limit":300,"consumedLimit":300,"totalLimit":200000,"rem_limit":199700,"isverify":"1"}}""")) as CustomerLookup.Found
        assertThat(found.customer.name).isEqualTo("Nikhil K Gupta")
        assertThat(found.customer.mobile).isEqualTo("9876543210") // the masked cust_mob is never used
        assertThat(found.customer.leftLimit).isEqualTo(BigDecimal(199700))
        assertThat(readLookup("9876543210", el("""{"status":false,"message":"User not exist"}"""))).isEqualTo(CustomerLookup.NotRegistered)
        assertThat(readLookup("9876543210", el("""{"status":true,"message":"Verify your OTP","otpReference":"remVerification"}"""))).isEqualTo(CustomerLookup.NeedsOtp)
        assertThat(readLookup("9876543210", el("""{"status":false,"message":"Provider down"}"""))).isNull() // never guessed as "not registered"
    }

    @Test fun `recipients read the old bene_ fields, verified from any of the flags`() {
        val list = readRecipients(el("""[{"id":1654,"bene_name":"NIKHIL KUMAR","bene_bank":"UCO Bank","bene_accno":"32840110063874","bene_ifsc":"ucba0003284","bene_mobno":"9123456780","isBankVerified":1},
            {"id":"1655","bene_name":"Amit","bene_bank":"Axis","bene_accno":"9177","isverify":"0"},{"bene_name":"no id"}]"""))
        assertThat(list).hasSize(2)
        assertThat(list[0]).isEqualTo(nikhil)
        assertThat(list[1].verified).isFalse()
        assertThat(readRecipients(el("""{"data":[]}"""))).isEmpty()
    }

    @Test fun `transfer result - status, every bank ref of a split, ASL id`() {
        val (st, refs, txn) = readTransfer(el("""{"transactionstatus":"SUCCESS","providerTransId":"6308","systemTransId":"DMT63"}"""))
        assertThat(st).isEqualTo("SUCCESS"); assertThat(refs).containsExactly("6308"); assertThat(txn).isEqualTo("DMT63")
        val split = readTransfer(el("""{"systemTransId":"DMT64","transactions":[{"status":"SUCCESS","utr":"A1"},{"status":"PENDING","utr":"A2"}]}"""))
        assertThat(split.first).isEqualTo("PENDING")
        assertThat(split.second).containsExactly("A1", "A2").inOrder()
    }

    @Test fun `name check and charges read leniently`() {
        assertThat(readName(el("""{"name":"NIKHIL KUMAR"}"""))).isEqualTo("NIKHIL KUMAR")
        assertThat(readName(el("""{"data":{"beneName":"SITA DEVI"}}"""))).isEqualTo("SITA DEVI")
        assertThat(readName(el("""{"name":""}"""))).isNull()
        assertThat(readCharges(el("""{"charge":"50"}"""), 5000)).isEqualTo(Charges(BigDecimal(50), BigDecimal(5050)))
        assertThat(readCharges(el("""{"x":1}"""), 5000)).isNull()
    }

    @Test fun `typed vs bank name - different first name only`() {
        assertThat(DmtRules.namesDiffer("Rakesh Kumar", "NIKHIL KUMAR")).isTrue()
        assertThat(DmtRules.namesDiffer("nikhil", "NIKHIL KUMAR")).isFalse()
        assertThat(DmtRules.namesDiffer("", "NIKHIL KUMAR")).isFalse()
    }

    @Test fun `recipient form - account twice, IFSC, name and mobile, name check needs the bank details`() {
        val ok = RecipientForm(DmtBank("1", "UCO Bank", "UCBA0003284"), "32840110063874", "32840110063874", "UCBA0003284", "Nikhil Kumar", "9123456780")
        assertThat(ok.problems()).isEmpty()
        assertThat(ok.copy(confirm = "1").problems()).containsExactly(RecipientForm.Field.Confirm)
        assertThat(ok.copy(mobile = "123").problems()).containsExactly(RecipientForm.Field.Mobile)
        assertThat(ok.copy(name = "").canCheckName()).isTrue()
        assertThat(ok.copy(ifsc = "x").canCheckName()).isFalse()
        assertThat(ok.copy(bankName = "NIKHIL KUMAR", name = "nikhil kumar").verified).isTrue()
    }

    @Test fun `amounts are whole rupees and never split by the app`() {
        assertThat(DmtRules.amountOf("25000")).isEqualTo(25000)
        assertThat(DmtRules.amountOf("0")).isNull()
        assertThat(DmtRules.amountOf("9")).isNull() // server minimum ₹10 (UAT 5 Nov)
        assertThat(DmtRules.amountOf("10")).isEqualTo(10)
        assertThat(DmtRules.cleanMobile("+91 98765-43210")).isEqualTo("9876543210")
        assertThat(DmtRules.search(listOf(nikhil), "3874")).containsExactly(nikhil)
        assertThat(DmtRules.search(listOf(nikhil), "uco")).containsExactly(nikhil)
    }
}

class DmtRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repo: DmtRepositoryImpl

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = DmtRepositoryImpl(retrofit.create(DmtApi::class.java), json)
    }
    @After fun tearDown() = server.shutdown()
    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    private fun input(key: IdempotencyKey = IdempotencyKey.new()) = TransferInput(customer, nikhil, TransferMode.Imps, 5000, BigDecimal(50), "1234", key)

    @Test fun `lookup and OTP send the purpose tags the backend expects`() = runTest {
        respond(200, """{"status":true,"data":{"statusCheck":"0"}}""")
        respond(200, """{"status":true,"data":{}}""")
        respond(200, """{"status":true,"data":{}}""")
        repo.lookup("9876543210")
        repo.sendOtp("9876543210", OtpPurpose.Customer)
        repo.verifyOtp("9123456780", OtpPurpose.Recipient, "4821", "32840110063874")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"phone":"9876543210","otpReference":"remVerification"}""")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"phone":"9876543210","otpReference":"remVerification"}""")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"phone":"9123456780","otpReference":"benVerification","otp":"4821","beneficiaryAccno":"32840110063874"}""")
    }

    @Test fun `transfer - Idempotency-Key header, no apiToken, beneficiary id and the retailer's OTP`() = runTest {
        respond(200, """{"status":true,"data":{"transactionstatus":"SUCCESS","providerTransId":"6308","systemTransId":"DMT63"}}""")
        val key = IdempotencyKey.new()
        val o = repo.transfer(input(key)) as DmtOutcome.Sent
        assertThat(o.receipt.bankRefs).containsExactly("6308")
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo(key.value)
        val body = req.body.readUtf8()
        assertThat(body).isEqualTo("""{"phone":"9876543210","beneficiaryId":"1654","transferType":"IMPS","amount":"5000","otp":"1234"}""")
        assertThat(body).doesNotContain("apiToken")
    }

    @Test fun `transfer - 5xx is Pending, OTP refusal is WrongOtp, other 4xx Failed, timeout Pending`() = runTest {
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.transfer(input())).isInstanceOf(DmtOutcome.Pending::class.java)
        respond(400, """{"status":false,"message":"Invalid OTP"}""")
        assertThat(repo.transfer(input())).isInstanceOf(DmtOutcome.WrongOtp::class.java)
        respond(422, """{"status":false,"statusCode":422,"message":"Validation failed","error":{"code":"VALIDATION_ERROR","message":"Minimum transfer amount is ₹10"}}""")
        assertThat(repo.transfer(input())).isEqualTo(DmtOutcome.Refused("Minimum transfer amount is ₹10")) // refused, nothing moved
        respond(200, """{"status":true,"data":{"transactionstatus":"ACCEPTED"}}""")
        assertThat(repo.transfer(input())).isInstanceOf(DmtOutcome.Pending::class.java)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThat(repo.transfer(input())).isInstanceOf(DmtOutcome.Pending::class.java)
    }

    @Test fun `UAT 5 Nov - Invalid OTP inside a 200 status-true answer is a failure, not a success`() = runTest {
        respond(200, """{"status":true,"statusCode":200,"message":"Invalid OTP.","data":{"status":false,"message":"Invalid OTP."}}""")
        val r = repo.verifyOtp("9123456780", OtpPurpose.Recipient, "1111", "32840110063874") as NetworkResult.Failure.Api
        assertThat(r.message).isEqualTo("Invalid OTP.")
        respond(200, """{"status":true,"message":"Remitter registered successfully. OTP sent for verification.","data":{"status":true,"message":"Remitter registered successfully. OTP sent for verification.","otpReference":"remVerification"}}""")
        assertThat(repo.register("9876543210", "Test", Gender.Male)).isInstanceOf(NetworkResult.Success::class.java)
        respond(200, """{"status":true,"message":"x","data":{"status":false,"message":"Invalid OTP"}}""")
        assertThat(repo.transfer(input())).isInstanceOf(DmtOutcome.WrongOtp::class.java)
        respond(200, """{"status":true,"message":"x","data":{"status":false,"message":"Insufficient balance"}}""")
        assertThat((repo.transfer(input()) as DmtOutcome.Failed).reason).isEqualTo("Insufficient balance")
        respond(200, """{"status":true,"message":"User not exist","data":{"status":false,"message":"User not exist"}}""")
        assertThat((repo.lookup("9876543210") as NetworkResult.Success).data).isEqualTo(CustomerLookup.NotRegistered)
        respond(200, """{"status":true,"message":"x","data":{"status":false,"message":"Provider down"}}""")
        assertThat((repo.lookup("9876543210") as NetworkResult.Failure.Api).message).isEqualTo("Provider down")
    }

    @Test fun `UAT 5 Nov - 429 rate limit message reaches the screen`() = runTest {
        respond(429, """{"status":"fail","statusCode":429,"message":"Too many sensitive requests (OTP/Password Reset), please try again after 15 minutes","data":null}""")
        assertThat((repo.verifyOtp("9876543210", OtpPurpose.Customer, "1111") as NetworkResult.Failure.Api).message).startsWith("Too many sensitive requests")
    }

    @Test fun `charges API missing (404 on UAT) is no charge, not an error`() = runTest {
        respond(404, """{"status":false,"statusCode":404,"message":"Cannot POST"}""")
        assertThat((repo.charges(5000, TransferMode.Imps) as NetworkResult.Success).data).isNull()
    }

    @Test fun `add recipient sends the bank's name when checked, and the recipient mobile`() = runTest {
        respond(200, """{"status":true,"data":{}}""")
        val f = RecipientForm(DmtBank("1", "UCO Bank", null), "32840110063874", "32840110063874", "ucba0003284", "Nikhil Kumar", "9123456780", bankName = "NIKHIL KUMAR")
        repo.addRecipient("9876543210", f)
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo(
            """{"phone":"9876543210","beneficiaryName":"NIKHIL KUMAR","beneficiaryAccNo":"32840110063874","bankIfscCod":"UCBA0003284","beneficiaryBankName":"UCO Bank","beneficiaryMobileNumber":"9123456780","trnasferMode":"IMPS","isVerified":"1"}""",
        )
    }
}

// ---------------------------------------------------------------- ViewModel

private class FakeRepo : DmtRepository {
    var lookup: CustomerLookup = CustomerLookup.Found(customer)
    var recipients = listOf(nikhil)
    var name = "NIKHIL KUMAR"
    var charges: Charges? = Charges(BigDecimal(50), BigDecimal(5050))
    var balance = BigDecimal(10000)
    val outcomes = ArrayDeque<DmtOutcome>()
    val keys = mutableListOf<IdempotencyKey>()
    var nameChecks = 0
    var addResult: NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    var verifyResult: NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    var otpSends = 0
    var registered = 0
    val verified = mutableListOf<OtpPurpose>()

    fun receipt() = DmtReceipt("Rahul", "9876543210", "NIKHIL", "UCO", "3874", "IMPS", BigDecimal(5000), null, emptyList(), null, "")
    override suspend fun lookup(mobile: String) = NetworkResult.Success(lookup, null)
    override suspend fun register(mobile: String, name: String, gender: Gender): NetworkResult<Unit> { registered++; return NetworkResult.Success(Unit, null) }
    override suspend fun sendOtp(phone: String, purpose: OtpPurpose, account: String?): NetworkResult<Unit> { otpSends++; return NetworkResult.Success(Unit, null) }
    override suspend fun verifyOtp(phone: String, purpose: OtpPurpose, otp: String, account: String?): NetworkResult<Unit> {
        if (verifyResult is NetworkResult.Failure) return verifyResult
        verified += purpose; if (purpose == OtpPurpose.Customer) lookup = CustomerLookup.Found(customer); return NetworkResult.Success(Unit, null)
    }
    override suspend fun recipients(mobile: String) = NetworkResult.Success(recipients, null)
    override suspend fun banks() = NetworkResult.Success(listOf(DmtBank("1", "UCO Bank", "UCBA0003284")), null)
    override suspend fun checkName(form: RecipientForm): NetworkResult<String> { nameChecks++; return NetworkResult.Success(name, null) }
    override suspend fun addRecipient(customerMobile: String, form: RecipientForm) = addResult
    override suspend fun charges(amount: Int, mode: TransferMode) = NetworkResult.Success(charges, null)
    override suspend fun sendTransferOtp() = NetworkResult.Success(Unit, null)
    override suspend fun transfer(input: TransferInput): DmtOutcome { keys += input.key; return outcomes.removeFirstOrNull() ?: DmtOutcome.Pending(receipt()) }
    override suspend fun balance() = NetworkResult.Success(balance, null)
}

private class FakeUser(regno: Long = 6044) : CurrentUser {
    override val user = MutableStateFlow<UserSummary?>(UserSummary("Ajay Kumar", "ASL Payzones", regno, "Retailer", true))
    override suspend fun logout() = Unit
}

private class FakeRecent : RecentCustomerStore {
    val data = MutableStateFlow<Map<Long, List<RecentCustomer>>>(emptyMap())
    override fun recent(regno: Long) = data.map { it[regno].orEmpty() }
    override suspend fun remember(regno: Long, mobile: String, name: String, now: Long) {
        data.value = data.value + (regno to (listOf(RecentCustomer(mobile, name, now)) + data.value[regno].orEmpty().filter { it.mobile != mobile }).take(10))
    }
    override suspend fun noteTransfer(regno: Long, mobile: String, note: String, now: Long) {
        data.value = data.value + (regno to data.value[regno].orEmpty().map { if (it.mobile == mobile) it.copy(last = note, at = now) else it })
    }
    override suspend fun clear(regno: Long) { data.value = data.value - regno }
    override suspend fun clearAll() { data.value = emptyMap() }
}

/** The real store against a real DataStore file, with a stand-in cipher (the Keystore isn't on the JVM). */
class RecentCustomerStoreTest {
    @get:Rule val tmp = org.junit.rules.TemporaryFolder()
    private object Rot : StringCipher {
        override fun encrypt(plain: String) = java.util.Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
        override fun decrypt(cipher: String) = String(java.util.Base64.getDecoder().decode(cipher)).reversed()
    }

    @Test fun `kept per retailer, newest first, at most 10, never in plain text, cleared on sign-out`() = runTest {
        val file = java.io.File(tmp.root, "dmt_test.preferences_pb")
        val ds = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = EncryptedRecentCustomerStore(ds, Rot, json)
        repeat(12) { i -> store.remember(6044, "98765432${10 + i}", "Customer $i", i.toLong()) }
        store.remember(6044, "9876543210", "Customer 0", 100) // seen again → moves to the top, no duplicate
        store.noteTransfer(6044, "9876543210", "₹ 100 to AJAY KUMAR", 101)
        val list = store.recent(6044).first()
        assertThat(list).hasSize(RecentCustomerStore.MAX)
        assertThat(list.first()).isEqualTo(RecentCustomer("9876543210", "Customer 0", 101, "₹ 100 to AJAY KUMAR"))
        assertThat(list.map { it.mobile }.distinct()).hasSize(10)
        assertThat(store.recent(1111).first()).isEmpty() // another retailer sees nothing
        assertThat(file.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("9876543210")
        assertThat(file.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("Customer")
        store.clearAll()
        assertThat(store.recent(6044).first()).isEmpty()
    }
}

class DmtViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private suspend fun DmtViewModel.toReview(amount: String = "5000") {
        onMobile("9876543210"); lookup(); pickRecipient(nikhil); onAmount(amount); review()
    }

    @Test fun `new customer registers, verifies with the customer OTP, then is found`() = runTest {
        val repo = FakeRepo().apply { lookup = CustomerLookup.NotRegistered }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Register)
        vm.onRegName("Rahul Kumar"); vm.register(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Customer)
        vm.onOtp("4821"); vm.submitOtp(); advanceUntilIdle()
        assertThat(repo.verified).containsExactly(OtpPurpose.Customer)
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Customer)
        assertThat(vm.state.value.recipients).containsExactly(nikhil)
    }

    @Test fun `recent customers - remembered after lookup, search by name or digits, tap opens, transfer adds a note`() = runTest {
        val recent = FakeRecent()
        val repo = FakeRepo().apply { outcomes += DmtOutcome.Sent(receipt()) }
        val vm = DmtViewModel(repo, recent, FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle()
        assertThat(vm.state.value.recent.map { it.mobile }).containsExactly("9876543210")
        vm.changeCustomer()
        vm.onMobile("rah"); assertThat(vm.state.value.visibleRecent.map { it.name }).containsExactly("Rahul Kumar")
        assertThat(vm.state.value.mobile).isEmpty() // letters never become a number
        vm.onMobile("9876"); assertThat(vm.state.value.visibleRecent).hasSize(1)
        vm.onMobile("5555"); assertThat(vm.state.value.visibleRecent).isEmpty()
        vm.onMobile(""); vm.pickRecent(vm.state.value.recent.first()); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Customer)
        vm.pickRecipient(nikhil); vm.onAmount("100"); vm.review(); advanceUntilIdle()
        vm.slideToSend(); advanceUntilIdle(); vm.onOtp("1234"); vm.submitOtp(); advanceUntilIdle()
        assertThat(recent.data.value[6044L]!!.first().last).isEqualTo("₹ 100 to NIKHIL KUMAR")
        vm.backToCustomer(); vm.changeCustomer(); vm.clearRecent(); advanceUntilIdle()
        assertThat(vm.state.value.recent).isEmpty()
    }

    @Test fun `unverified customer goes straight to the OTP`() = runTest {
        val vm = DmtViewModel(FakeRepo().apply { lookup = CustomerLookup.NeedsOtp }, FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Customer)
    }

    @Test fun `name check is optional and asks before spending, a different name blocks saving until chosen`() = runTest {
        val repo = FakeRepo()
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle(); vm.startAddRecipient(); advanceUntilIdle()
        vm.onBank(DmtBank("1", "UCO Bank", "UCBA0003284")); vm.onAccount("32840110063874"); vm.onConfirm("32840110063874"); vm.onRecipientMobile("9123456780"); vm.onRecipientName("Rakesh Kumar")
        assertThat(vm.state.value.form.ifsc).isEqualTo("UCBA0003284") // filled from the bank's sample
        vm.askNameCheck()
        assertThat(vm.state.value.nameCheck).isEqualTo(NameCheck.Confirm)
        assertThat(repo.nameChecks).isEqualTo(0) // nothing spent before the retailer confirms
        vm.runNameCheck(); advanceUntilIdle()
        assertThat((vm.state.value.nameCheck as NameCheck.Done).differs).isTrue()
        vm.saveRecipient(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isNull()
        vm.useBankName(); vm.saveRecipient(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Recipient)
        assertThat(vm.state.value.form.verified).isTrue()
    }

    @Test fun `wrong recipient OTP keeps the sheet open with the message, never shows Saved`() = runTest {
        val repo = FakeRepo().apply { verifyResult = NetworkResult.Failure.Api(422, "INNER_STATUS", "Invalid OTP.") }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle(); vm.startAddRecipient(); advanceUntilIdle()
        vm.onBank(DmtBank("1", "UCO Bank", "UCBA0003284")); vm.onAccount("32840110063874"); vm.onConfirm("32840110063874"); vm.onRecipientMobile("9123456780"); vm.onRecipientName("Nikhil Kumar")
        vm.saveRecipient(); advanceUntilIdle()
        vm.onOtp("1111"); vm.submitOtp(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Recipient)
        assertThat(vm.state.value.page).isEqualTo(DmtPage.AddRecipient)
        assertThat(vm.state.value.otpUiError).isEqualTo(com.asl.wallet.core.network.UiError.Server("Invalid OTP."))
    }

    @Test fun `already added but unconfirmed recipient gets a new OTP instead of an error`() = runTest {
        val repo = FakeRepo().apply { addResult = NetworkResult.Failure.Api(400, "BAD_REQUEST", "Beneficiary already exist") }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle(); vm.startAddRecipient(); advanceUntilIdle()
        vm.onBank(DmtBank("1", "UCO Bank", "UCBA0003284")); vm.onAccount("32840110063874"); vm.onConfirm("32840110063874"); vm.onRecipientMobile("9123456780"); vm.onRecipientName("Nikhil Kumar")
        vm.saveRecipient(); advanceUntilIdle()
        assertThat(repo.otpSends).isEqualTo(1)
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Recipient)
        assertThat(vm.state.value.recipientExisted).isTrue()
        assertThat(vm.state.value.error).isNull()
    }

    @Test fun `saving without a name check is allowed (optional)`() = runTest {
        val vm = DmtViewModel(FakeRepo(), FakeRecent(), FakeUser())
        vm.onMobile("9876543210"); vm.lookup(); advanceUntilIdle(); vm.startAddRecipient(); advanceUntilIdle()
        vm.onBank(DmtBank("1", "UCO Bank", "UCBA0003284")); vm.onAccount("32840110063874"); vm.onConfirm("32840110063874"); vm.onRecipientMobile("9123456780"); vm.onRecipientName("Nikhil Kumar")
        vm.saveRecipient(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Recipient)
        vm.onOtp("1234"); vm.submitOtp(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.RecipientSaved)
    }

    @Test fun `review shows charge and total before the OTP, low balance or over limit never asks for the OTP`() = runTest {
        val repo = FakeRepo().apply { balance = BigDecimal(1000) }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.toReview(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Review)
        assertThat(vm.state.value.total).isEqualTo(BigDecimal(5050))
        assertThat(vm.state.value.shortBy).isEqualTo(BigDecimal(4050))
        vm.slideToSend(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isNull()

        val vm2 = DmtViewModel(FakeRepo(), FakeRecent(), FakeUser())
        vm2.onMobile("9876543210"); vm2.lookup(); vm2.pickRecipient(nikhil); vm2.onAmount("20000")
        assertThat(vm2.state.value.overLimit).isTrue() // ₹ 19,500 left
        vm2.review(); advanceUntilIdle()
        assertThat(vm2.state.value.page).isEqualTo(DmtPage.Amount)
    }

    @Test fun `one transfer per confirmation - wrong OTP gets a fresh key and stays, success ends the key`() = runTest {
        val repo = FakeRepo().apply {
            outcomes += DmtOutcome.WrongOtp("Invalid OTP")
            outcomes += DmtOutcome.Sent(receipt())
        }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.toReview(); advanceUntilIdle()
        vm.slideToSend(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Transfer)
        vm.onOtp("1111"); vm.submitOtp(); advanceUntilIdle()
        assertThat(vm.state.value.otpFor).isEqualTo(OtpFor.Transfer)
        assertThat(vm.state.value.otpError).isEqualTo("Invalid OTP")
        vm.onOtp("1234"); vm.submitOtp(); runCurrent()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Sending) // an instant answer still shows "Sending" for ~2 s
        advanceTimeBy(1_500); runCurrent()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Sending)
        advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Result)
        assertThat(repo.keys).hasSize(2)
        assertThat(repo.keys[0]).isNotEqualTo(repo.keys[1])
        vm.submitOtp(); advanceUntilIdle()
        assertThat(repo.keys).hasSize(2) // finished: nothing more goes out
    }

    @Test fun `a refused transfer (4xx) goes back to the amount with the reason, never a failed receipt`() = runTest {
        val repo = FakeRepo().apply { outcomes += DmtOutcome.Refused("Minimum transfer amount is ₹10") }
        val vm = DmtViewModel(repo, FakeRecent(), FakeUser())
        vm.toReview(); advanceUntilIdle(); vm.slideToSend(); advanceUntilIdle(); vm.onOtp("1234"); vm.submitOtp(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Amount)
        assertThat(vm.state.value.outcome).isNull()
        assertThat(vm.state.value.error).isEqualTo(com.asl.wallet.core.network.UiError.Server("Minimum transfer amount is ₹10"))
    }

    @Test fun `no charges API yet - review still works and says the charge is as per plan`() = runTest {
        val vm = DmtViewModel(FakeRepo().apply { charges = null }, FakeRecent(), FakeUser())
        vm.toReview(); advanceUntilIdle()
        assertThat(vm.state.value.page).isEqualTo(DmtPage.Review)
        assertThat(vm.state.value.chargesKnown).isFalse()
        assertThat(vm.state.value.total).isEqualTo(BigDecimal(5000))
    }
}
