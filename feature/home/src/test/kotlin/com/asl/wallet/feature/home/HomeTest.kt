package com.asl.wallet.feature.home

import com.asl.wallet.feature.home.domain.HomeLayout
import com.asl.wallet.feature.home.domain.HomeSection
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.security.storage.StringCipher
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.home.data.DataStoreHomeLocalStore
import com.asl.wallet.feature.home.data.HomeApi
import com.asl.wallet.feature.home.data.HomeLocalStore
import com.asl.wallet.feature.home.data.HomeRepository
import com.asl.wallet.feature.home.data.HomeRepositoryImpl
import com.asl.wallet.feature.home.data.ServiceDto
import com.asl.wallet.feature.home.data.ServicesResult
import com.asl.wallet.feature.home.domain.HomeService
import com.asl.wallet.feature.home.domain.HomeSnapshot
import com.asl.wallet.feature.home.domain.LedgerEntry
import com.asl.wallet.feature.home.domain.ServiceCatalogue
import com.asl.wallet.feature.home.domain.ServiceDestination
import com.asl.wallet.feature.home.domain.ServiceGroup
import com.asl.wallet.feature.home.domain.ServiceKind
import com.asl.wallet.feature.home.domain.WalletSummary
import com.asl.wallet.feature.home.ui.HomeEvent
import com.asl.wallet.feature.home.ui.HomeViewModel
import com.asl.wallet.feature.home.ui.ServiceSheet
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal

private fun dto(name: String, order: Int? = null, link: String? = null, kyc: JsonPrimitive? = null, status: JsonPrimitive? = null) =
    ServiceDto(services_name = name, service_order = order, services_link = link, isKycRequired = kyc, status = status)

class ServiceCatalogueTest {

    @Test fun `classifies the real service names`() {
        val cases = mapOf(
            "AEPS Yes Bank" to ServiceKind.Aeps, "Micro ATM" to ServiceKind.MicroAtm,
            "DMT" to ServiceKind.MoneyTransfer, "Money transfer" to ServiceKind.MoneyTransfer, "UPI transfer" to ServiceKind.Upi,
            "Card to cash" to ServiceKind.CardToCash, "SBI CSP" to ServiceKind.Bank, "PNB BC" to ServiceKind.Bank,
            "DTH recharge" to ServiceKind.Dth, "Mobile recharge" to ServiceKind.Mobile, "Electricity bill" to ServiceKind.Electricity,
            "BBPS" to ServiceKind.Bills, "Utility bill" to ServiceKind.Bills, "FASTag" to ServiceKind.Fastag,
            "LIC premium" to ServiceKind.Insurance, "PAN card" to ServiceKind.Pan, "NSDL PAN" to ServiceKind.Pan,
            "IRCTC train" to ServiceKind.Travel, "Google Play gift card" to ServiceKind.Gift, "Something new" to ServiceKind.Other,
        )
        cases.forEach { (name, kind) -> assertWithMessage(name).that(ServiceCatalogue.classify(name).first).isEqualTo(kind) }
        assertThat(ServiceCatalogue.groupOf(ServiceKind.Bank)).isEqualTo(ServiceGroup.Banking)
        assertThat(ServiceCatalogue.groupOf(ServiceKind.Fastag)).isEqualTo(ServiceGroup.RechargeBills)
        assertThat(ServiceCatalogue.groupOf(ServiceKind.Travel)).isEqualTo(ServiceGroup.More)
    }

    @Test fun `partner links must be plain https with a real host`() {
        assertThat(ServiceCatalogue.safePartnerUrl("https://www.irctc.co.in/nget")).isEqualTo("https://www.irctc.co.in/nget")
        listOf(
            null, "", "http://www.irctc.co.in", "javascript:alert(1)", "intent://scan#Intent;end", "file:///sdcard/x.html",
            "https://asl@evil.example.com", "https://localhost/x", "content://com.asl/x", "https:///nohost", "not a url",
        ).forEach { assertWithMessage(it.toString()).that(ServiceCatalogue.safePartnerUrl(it)).isNull() }
    }

    @Test fun `server list is filtered, sorted, de-duplicated and routed`() {
        val list = ServiceCatalogue.fromServer(
            listOf(
                dto("SBI CSP", order = 3, link = "https://csp.sbi.co.in"),
                dto("AEPS", order = 1),
                dto("aeps", order = 9),                                     // duplicate
                dto("Old service", order = 2, status = JsonPrimitive(0)),   // inactive
                dto("IRCTC", order = 4, link = "http://irctc.co.in"),       // not https
                dto("  ", order = 5),                                       // no name
            ),
            built = emptySet(),
        )
        assertThat(list.map { it.name }).containsExactly("AEPS", "SBI CSP", "IRCTC").inOrder()
        assertThat(list[0].destination).isEqualTo(ServiceDestination.ComingSoon)            // built = emptySet() in this call
        assertThat(list[1].destination).isEqualTo(ServiceDestination.Partner("https://csp.sbi.co.in"))
        assertThat(list[2].destination).isEqualTo(ServiceDestination.ComingSoon)

        val built = ServiceCatalogue.fromServer(listOf(dto("AEPS")), built = setOf(3))
        assertThat(built.single().destination).isEqualTo(ServiceDestination.App(3))
    }

    @Test fun `AEPS opens in the app and Aadhaar Pay is hidden`() {
        val list = ServiceCatalogue.fromServer(listOf(dto("AEPS (KOTAK BANK)", 1), dto("Aadhaar Pay", 2), dto("AADHAR PAY", 3), dto("Micro ATM", 4)))
        assertThat(list.map { it.name }).containsExactly("AEPS", "Micro ATM").inOrder()
        // Two AEPS banks from the server are one AEPS tile; the AEPS flow asks for the bank.
        val two = ServiceCatalogue.fromServer(listOf(dto("AEPS (YES BANK)", 1), dto("DMT", 2), dto("AEPS (KOTAK BANK)", 3)))
        assertThat(two.map { it.name }).containsExactly("AEPS", "DMT").inOrder()
        assertThat(two.first().key).isEqualTo(ServiceCatalogue.AEPS_KEY)
        assertThat(list[0].destination).isEqualTo(ServiceDestination.App(ServiceCatalogue.AEPS_MODULE))
        assertThat(list[1].destination).isEqualTo(ServiceDestination.ComingSoon)
        assertThat(ServiceCatalogue.fallback().first { it.kind == ServiceKind.Aeps }.destination).isEqualTo(ServiceDestination.App(3))
    }

    @Test fun `Payout opens the wallet's send-to-own-bank screen, DMT stays separate`() {
        val list = ServiceCatalogue.fromServer(listOf(dto("Payout", 1), dto("Settlement", 2), dto("Money Transfer", 3)))
        assertThat(list.map { it.destination }).containsExactly(
            ServiceDestination.App(ServiceCatalogue.PAYOUT_MODULE),
            ServiceDestination.App(ServiceCatalogue.PAYOUT_MODULE),
            ServiceDestination.App(ServiceCatalogue.DMT_MODULE),
        ).inOrder()
        assertThat(ServiceCatalogue.fallback().first { it.name == "Payout" }.destination).isEqualTo(ServiceDestination.App(ServiceCatalogue.PAYOUT_MODULE))
    }

    @Test fun `pinned uses saved order, drops stale keys, else the defaults`() {
        val all = ServiceCatalogue.fallback()
        val defaults = ServiceCatalogue.pinned(all, null)
        assertThat(defaults.map { it.kind }).containsExactly(
            ServiceKind.Aeps, ServiceKind.MicroAtm, ServiceKind.MoneyTransfer, ServiceKind.Upi, ServiceKind.Mobile, ServiceKind.Bills, ServiceKind.CardToCash,
        ).inOrder()
        assertThat(ServiceCatalogue.pinned(all, listOf("fastag", "gone", "aeps", "aeps")).map { it.key }).containsExactly("fastag", "aeps").inOrder()
        assertThat(ServiceCatalogue.pinned(all, listOf("gone"))).isEqualTo(defaults)
        assertThat(ServiceCatalogue.pinned(all, all.map { it.key })).hasSize(ServiceCatalogue.MAX_PINNED)
    }

    @Test fun `fallback never invents partner URLs and search is case-insensitive`() {
        val all = ServiceCatalogue.fallback()
        assertThat(all.none { it.destination is ServiceDestination.Partner }).isTrue()
        assertThat(all.map { it.key }.toSet()).hasSize(all.size)
        assertThat(ServiceCatalogue.search(all, "  recharge ").map { it.name }).containsExactly("Mobile recharge", "DTH recharge")
        assertThat(ServiceCatalogue.search(all, "")).isEqualTo(all)
    }
}

// ------------------------------------------------------------------------------------------------

private class MemLocal : HomeLocalStore {
    var snap: HomeSnapshot? = null
    val pinsByRegno = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    override suspend fun snapshot() = snap
    override suspend fun saveWallet(wallet: WalletSummary) { snap = HomeSnapshot(wallet, snap?.recent.orEmpty()) }
    override suspend fun saveRecent(recent: List<LedgerEntry>) { snap = HomeSnapshot(snap?.wallet, recent) }
    override fun pins(regno: Long): Flow<List<String>?> = pinsByRegno.map { it[regno] }
    override suspend fun savePins(regno: Long, keys: List<String>) { pinsByRegno.value = pinsByRegno.value + (regno to keys) }
    val sectionsByRegno = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    override fun sections(regno: Long): Flow<List<String>?> = sectionsByRegno.map { it[regno] }
    override suspend fun saveSections(regno: Long, keys: List<String>) { sectionsByRegno.value = sectionsByRegno.value + (regno to keys) }
    override suspend fun clearCache() { snap = null }
}

class HomeRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val local = MemLocal()
    private lateinit var repo: HomeRepositoryImpl

    @Before fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(HomeApi::class.java)
        repo = HomeRepositoryImpl(api, local, json) { 1_000L }
    }

    @After fun tearDown() = server.shutdown()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test fun `account info with string amounts (UAT shape) is parsed and cached`() = runTest {
        respond(200, """{"status":true,"statusCode":200,"message":"ok","data":{"account_name":"Ajay Kumar","e_topup_purchase":"23.00","commission_received":"0.00",
            "available_balance_amt":"23.00","availableBalanceAmt":"7,297.16","commissionReceived":"1377.16","regno":6044,"phone":"9798143077","status":1}}""")
        val r = repo.wallet() as NetworkResult.Success
        assertThat(r.data).isEqualTo(WalletSummary(BigDecimal("7297.16"), BigDecimal("1377.16"), 1_000L))
        assertThat(local.snap?.wallet).isEqualTo(r.data)
        assertThat(server.takeRequest().path).isEqualTo("/merchant/my-wallet/account-info")
    }

    @Test fun `numeric amounts and snake-only fields also work`() = runTest {
        respond(200, """{"status":true,"data":{"available_balance_amt":23,"commission_received":null}}""")
        val r = repo.wallet() as NetworkResult.Success
        assertThat(r.data.balance).isEqualTo(BigDecimal("23"))
        assertThat(r.data.commission).isEqualTo(BigDecimal.ZERO)
    }

    @Test fun `missing balance is an error, not zero`() = runTest {
        respond(200, """{"status":true,"data":{"regno":6044}}""")
        assertThat(repo.wallet()).isInstanceOf(NetworkResult.Failure.Unexpected::class.java)
        assertThat(local.snap).isNull()
    }

    @Test fun `expired session maps to Unauthorized`() = runTest {
        respond(401, """{"status":false,"message":"Unauthorized"}""")
        assertThat(repo.wallet()).isEqualTo(NetworkResult.Failure.Unauthorized)
    }

    @Test fun `services 500 falls back, then keeps the last good server list`() = runTest {
        respond(500, """{"status":false,"statusCode":500,"message":"Invalid prisma.servicesSetting","error":{"code":"INTERNAL_SERVER_ERROR"}}""")
        val first = repo.services()
        assertThat(first.fromServer).isFalse()
        assertThat(first.services).isEqualTo(ServiceCatalogue.fallback())

        respond(200, """{"status":true,"data":[{"id":1,"services_name":"AEPS","service_order":1,"isKycRequired":1,"status":1},
            {"id":2,"services_name":"SBI CSP","services_link":"https://csp.sbi.co.in","service_order":2,"status":"1"}]}""")
        val second = repo.services()
        assertThat(second.fromServer).isTrue()
        assertThat(second.services.map { it.name }).containsExactly("AEPS", "SBI CSP", "Payout").inOrder()

        respond(500, """{"status":false}""")
        assertThat(repo.services()).isEqualTo(second)
    }

    @Test fun `services are read from the object shape UAT sends now, and Payout is never added twice`() = runTest {
        respond(200, """{"status":true,"data":{"totalServices":2,"services":[{"id":86,"services_name":"DMT","service_order":1,"status":1},{"id":9,"services_name":"Payout","service_order":2,"status":1}]}}""")
        val r = repo.services()
        assertThat(r.fromServer).isTrue()
        assertThat(r.services.map { it.name }).containsExactly("DMT", "Payout").inOrder()
    }

    @Test fun `account and Aadhaar numbers in ledger text are masked (UAT row)`() {
        assertThat(com.asl.wallet.feature.home.data.maskLongNumbers("CHARGES FOR VERIFY (A/C : 0185104000179539)"))
            .isEqualTo("CHARGES FOR VERIFY (A/C : ••••9539)")
        assertThat(com.asl.wallet.feature.home.data.maskLongNumbers("AEPS CW 2345 6789 0123 ref 12345678 on 24/09/2026"))
            .isEqualTo("AEPS CW ••••0123 ref 12345678 on 24/09/2026")
        assertThat(com.asl.wallet.feature.home.data.maskLongNumbers("FUND TRANSFER (DIRECT)")).isEqualTo("FUND TRANSFER (DIRECT)")
    }

    @Test fun `ledger rows become signed entries (UAT shape)`() = runTest {
        respond(200, """{"status":true,"data":[
            {"index":1,"id":7570198,"regno":6044,"transdesc":"FUND TRANSFER (DIRECT)","transDate":"24/09/2026","transTime":"06:32:37 PM ","creditAmt":20,"creditAmtFormatted":"20.00","debitAmt":0,"openingBal":0,"closingBal":20,"service":null,"referenceId":null,"status":"ONE"},
            {"id":7570199,"transdesc":"","service":"AEPS","transDate":"25/09/2026","transTime":"10:00:00 AM","creditAmt":0,"debitAmt":"150.50","closingBal":"-130.50"},
            {"transdesc":"no id, skipped"}],
            "pagination":{"currentPage":1,"limit":3,"totalRecords":2,"totalPages":1,"hasNext":false}}""")
        val r = repo.recent() as NetworkResult.Success
        assertThat(r.data).hasSize(2)
        assertThat(r.data[0]).isEqualTo(LedgerEntry(7570198, "FUND TRANSFER (DIRECT)", "24/09/2026", "06:32:37 PM", BigDecimal("20"), BigDecimal("20")))
        assertThat(r.data[1].title).isEqualTo("AEPS")
        assertThat(r.data[1].amount).isEqualTo(BigDecimal("-150.50"))
        assertThat(r.data[1].isCredit).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/merchant/ledger-report?page=1&limit=3")
        assertThat(local.snap?.recent).isEqualTo(r.data)
    }
}

// ------------------------------------------------------------------------------------------------

class HomeLocalStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Reversible fake that makes "is it encrypted at rest" checkable without Android Keystore. */
    private val cipher = object : StringCipher {
        override fun encrypt(plain: String) = "ENC:" + plain.reversed()
        override fun decrypt(cipher: String) = cipher.removePrefix("ENC:").reversed()
    }

    @Test fun `snapshot is encrypted at rest, cleared on sign-out, pins survive`() = runTest {
        val file = tmp.newFile("home_local.preferences_pb").also { it.delete() }
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        val store = DataStoreHomeLocalStore(ds, cipher, Json)
        store.saveWallet(WalletSummary(BigDecimal("7297.16"), BigDecimal("1377.16"), 5L))
        store.saveRecent(listOf(LedgerEntry(1, "FUND TRANSFER", "24/09/2026", "06:32 PM", BigDecimal("20"), BigDecimal("20"))))
        store.savePins(6044, listOf("aeps", "bad,key", "mobile recharge", "upitransfer"))

        assertThat(file.readBytes().toString(Charsets.UTF_8)).doesNotContain("7297.16")
        assertThat(store.snapshot()?.wallet?.balance).isEqualTo(BigDecimal("7297.16"))
        assertThat(store.snapshot()?.recent?.single()?.title).isEqualTo("FUND TRANSFER")
        assertThat(store.pins(6044).first()).containsExactly("aeps", "upitransfer").inOrder()
        assertThat(store.pins(1).first()).isNull()

        store.clearCache()
        assertThat(store.snapshot()).isNull()
        assertThat(store.pins(6044).first()).containsExactly("aeps", "upitransfer")
    }
}

// ------------------------------------------------------------------------------------------------

private class FakeUser(u: UserSummary?) : CurrentUser {
    val flow = MutableStateFlow(u)
    var loggedOut = false
    override val user: StateFlow<UserSummary?> = flow
    override suspend fun logout() { loggedOut = true; flow.value = null }
}

private class FakeHomeRepo : HomeRepository {
    var cache: HomeSnapshot? = null
    var walletResult: NetworkResult<WalletSummary> = NetworkResult.Success(WalletSummary(BigDecimal("7297.16"), BigDecimal("1377.16"), 1L), null)
    var recentResult: NetworkResult<List<LedgerEntry>> = NetworkResult.Success(emptyList(), null)
    var servicesResult = ServicesResult(
        ServiceCatalogue.fromServer(
            listOf(dto("AEPS", 1), dto("Micro ATM", 2), dto("Mobile recharge", 3), dto("IRCTC", 4, link = "https://www.irctc.co.in"), dto("FASTag", 5)),
        ),
        fromServer = true,
    )
    val pins = MutableStateFlow<List<String>?>(null)
    var savedFor: Long? = null
    override suspend fun cached() = cache
    override suspend fun wallet() = walletResult
    override suspend fun recent() = recentResult
    override suspend fun services() = servicesResult
    override fun pins(regno: Long): Flow<List<String>?> = pins
    override suspend fun savePins(regno: Long, keys: List<String>) { savedFor = regno; pins.value = keys }
    val sections = MutableStateFlow<List<String>?>(null)
    var sectionsSavedFor: Long? = null
    override fun sections(regno: Long): Flow<List<String>?> = sections
    override suspend fun saveSections(regno: Long, keys: List<String>) { sectionsSavedFor = regno; sections.value = keys }
}

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val retailer = UserSummary("Ajay Kumar", "ASL Payzones", 6044, "Retailer", kycDone = true)
    private val repo = FakeHomeRepo()

    private fun vm(user: UserSummary? = retailer) = FakeUser(user).let { it to HomeViewModel(repo, it) }

    @Test fun `first load shows balance, pinned services and recent rows`() = runTest {
        val row = LedgerEntry(1, "FUND TRANSFER", "24/09/2026", "06:32 PM", BigDecimal("20"), BigDecimal("20"))
        repo.recentResult = NetworkResult.Success(listOf(row), null)
        val (_, vm) = vm()
        runCurrent()
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.wallet?.balance).isEqualTo(BigDecimal("7297.16"))
        assertThat(s.pinned.map { it.name }).containsExactly("AEPS", "Micro ATM", "Mobile recharge").inOrder()
        assertThat(s.recent).containsExactly(row)
        assertThat(s.user?.firstName).isEqualTo("Ajay")
        assertThat(s.offline).isFalse()
    }

    @Test fun `Arrange Home - saved order shows, moves and saves for this retailer, cancel keeps the old order`() = runTest {
        repo.sections.value = listOf("recharge", "banking")
        val (_, vm) = vm()
        runCurrent()
        // Saved sections first, the rest after in their default order.
        assertThat(vm.state.value.sections.first()).isEqualTo(HomeSection.Recharge)
        assertThat(vm.state.value.sections).hasSize(HomeSection.entries.size)

        vm.startArrange()
        vm.moveSection(5, 0) // Recent to the top
        vm.cancelArrange()
        assertThat(vm.state.value.sections.first()).isEqualTo(HomeSection.Recharge)

        vm.startArrange()
        vm.moveSection(0, 1)
        vm.saveArrange()
        runCurrent()
        assertThat(vm.state.value.arranging).isNull()
        assertThat(vm.state.value.sections.take(2)).containsExactly(HomeSection.Banking, HomeSection.Recharge).inOrder()
        assertThat(repo.sectionsSavedFor).isEqualTo(6044L)
        assertThat(repo.sections.value!!.take(2)).containsExactly("banking", "recharge").inOrder()

        vm.startArrange(); vm.resetArrange(); vm.saveArrange(); runCurrent()
        assertThat(vm.state.value.sections).isEqualTo(HomeLayout.DEFAULT)
    }

    @Test fun `balance shows by default and only hides when the retailer toggles it`() = runTest {
        val (_, vm) = vm()
        runCurrent()
        assertThat(vm.state.value.balanceHidden).isFalse()
        // No auto-hide: it stays shown however long the screen is open (client request).
        advanceTimeBy(60_000)
        assertThat(vm.state.value.balanceHidden).isFalse()
        vm.toggleBalance()
        assertThat(vm.state.value.balanceHidden).isTrue()
        vm.toggleBalance()
        assertThat(vm.state.value.balanceHidden).isFalse()
    }

    @Test fun `offline with a saved balance shows it and flags offline`() = runTest {
        repo.cache = HomeSnapshot(WalletSummary(BigDecimal("100.00"), BigDecimal.ZERO, 1L), emptyList())
        repo.walletResult = NetworkResult.Failure.NoInternet
        repo.recentResult = NetworkResult.Failure.NoInternet
        val (_, vm) = vm()
        runCurrent()
        val s = vm.state.value
        assertThat(s.wallet?.balance).isEqualTo(BigDecimal("100.00"))
        assertThat(s.offline).isTrue()
        assertThat(s.walletError).isFalse()
        assertThat(s.recentError).isTrue()
    }

    @Test fun `no saved balance and a failure shows the error card`() = runTest {
        repo.walletResult = NetworkResult.Failure.Api(500, null, "boom")
        val (_, vm) = vm()
        runCurrent()
        assertThat(vm.state.value.walletError).isTrue()
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.offline).isFalse()
    }

    @Test fun `no KYC locks - AEPS, Recharge and BBPS open their modules, unbuilt services say coming soon`() = runTest {
        val (_, vm) = vm(retailer.copy(kycDone = false))
        runCurrent()
        val s = vm.state.value
        val aeps = s.services.first { it.name == "AEPS" }
        val mobile = s.services.first { it.name == "Mobile recharge" }
        val fastag = s.services.first { it.name == "FASTag" }
        val matm = s.services.first { it.name == "Micro ATM" }
        vm.events.test {
            vm.onServiceClick(aeps)
            assertThat(awaitItem()).isEqualTo(HomeEvent.OpenModule(3, aeps.key))
            vm.onServiceClick(mobile)
            assertThat(awaitItem()).isEqualTo(HomeEvent.OpenModule(ServiceCatalogue.RECHARGE_MODULE, mobile.key))
            vm.onServiceClick(fastag)
            assertThat(awaitItem()).isEqualTo(HomeEvent.OpenModule(ServiceCatalogue.BBPS_MODULE, fastag.key))
        }
        assertThat(vm.state.value.sheet).isNull()
        vm.onServiceClick(matm)
        assertThat(vm.state.value.sheet).isEqualTo(ServiceSheet.ComingSoon(matm))
    }

    @Test fun `partner site opens only after confirming, with the https URL`() = runTest {
        val (_, vm) = vm()
        runCurrent()
        val irctc = vm.state.value.services.first { it.name == "IRCTC" }
        vm.events.test {
            vm.onServiceClick(irctc)
            assertThat(vm.state.value.sheet).isEqualTo(ServiceSheet.Partner(irctc, "https://www.irctc.co.in"))
            expectNoEvents()
            vm.confirmPartner()
            assertThat(awaitItem()).isEqualTo(HomeEvent.OpenPartner("https://www.irctc.co.in"))
        }
        assertThat(vm.state.value.sheet).isNull()
    }

    @Test fun `edit pins keeps at most 7 and saves them for this retailer`() = runTest {
        repo.servicesResult = ServicesResult(ServiceCatalogue.fallback(), fromServer = false)
        val (_, vm) = vm()
        runCurrent()
        vm.startEdit()
        assertThat(vm.state.value.editSelection).hasSize(7)
        vm.toggleEdit("fastag")
        assertThat(vm.state.value.editSelection).doesNotContain("fastag") // full
        vm.toggleEdit("aeps"); vm.toggleEdit("fastag")
        assertThat(vm.state.value.editSelection!!.last()).isEqualTo("fastag")
        vm.saveEdit()
        advanceUntilIdle()
        assertThat(repo.savedFor).isEqualTo(6044L)
        assertThat(vm.state.value.editSelection).isNull()
        assertThat(vm.state.value.pinned.map { it.key }).doesNotContain("aeps")
        assertThat(vm.state.value.pinned.last().key).isEqualTo("fastag")
    }

    @Test fun `distributor is recognised and logout goes through the session owner`() = runTest {
        val (user, vm) = vm(retailer.copy(userType = "Distributor"))
        runCurrent()
        assertThat(vm.state.value.user?.isDistributor).isTrue()
        vm.logout()
        runCurrent()
        assertThat(user.loggedOut).isTrue()
        assertThat(vm.state.value.user).isNull()
    }

    @Test fun `search narrows the list`() = runTest {
        val (_, vm) = vm()
        runCurrent()
        vm.onQueryChange("atm")
        assertThat(vm.state.value.searchResults.map(HomeService::name)).containsExactly("Micro ATM")
    }
}

class HomeLayoutTest {
    @Test fun `parse keeps saved order, drops junk and repeats, and adds missing sections at the end`() {
        assertThat(HomeLayout.parse(null)).isEqualTo(HomeLayout.DEFAULT)
        val p = HomeLayout.parse(listOf("reports", "nope", "reports", " recharge "))
        assertThat(p.take(2)).containsExactly(HomeSection.Reports, HomeSection.Recharge).inOrder()
        assertThat(p.toSet()).isEqualTo(HomeSection.entries.toSet())
        assertThat(p).hasSize(HomeSection.entries.size)
    }

    @Test fun `move shifts one section and clamps out-of-range targets`() {
        val d = HomeLayout.DEFAULT
        assertThat(HomeLayout.move(d, 2, 0).first()).isEqualTo(HomeSection.Recharge)
        assertThat(HomeLayout.move(d, 0, 99).last()).isEqualTo(HomeSection.Banking)
        assertThat(HomeLayout.move(d, 0, -5)).isEqualTo(d)
        assertThat(HomeLayout.move(d, 42, 0)).isEqualTo(d)
        assertThat(HomeLayout.parse(HomeLayout.keys(HomeLayout.move(d, 3, 1)))).isEqualTo(HomeLayout.move(d, 3, 1))
    }
}
