package com.asl.wallet.feature.profile

import app.cash.turbine.test
import com.asl.wallet.core.common.session.CurrentUser
import com.asl.wallet.core.common.session.UserSummary
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.network.UiError
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.profile.data.BankingGroupDto
import com.asl.wallet.feature.profile.data.CommissionDto
import com.asl.wallet.feature.profile.data.OperatorDto
import com.asl.wallet.feature.profile.data.PendingSupportRepository
import com.asl.wallet.feature.profile.data.PrepaidGroupDto
import com.asl.wallet.feature.profile.data.ProfileApi
import com.asl.wallet.feature.profile.data.ProfileDto
import com.asl.wallet.feature.profile.data.ProfileRepository
import com.asl.wallet.feature.profile.data.ProfileRepositoryImpl
import com.asl.wallet.feature.profile.data.ServiceStatusDto
import com.asl.wallet.feature.profile.data.SlabDto
import com.asl.wallet.feature.profile.data.toActive
import com.asl.wallet.feature.profile.data.toCommission
import com.asl.wallet.feature.profile.data.toDetails
import com.asl.wallet.feature.profile.domain.ActiveService
import com.asl.wallet.feature.profile.domain.Commission
import com.asl.wallet.feature.profile.domain.OtpTarget
import com.asl.wallet.feature.profile.domain.ProfileDetails
import com.asl.wallet.feature.profile.ui.ChangePasswordEvent
import com.asl.wallet.feature.profile.ui.ChangePasswordViewModel
import com.asl.wallet.feature.profile.ui.ChangeStep
import com.asl.wallet.feature.profile.ui.ComplaintViewModel
import com.asl.wallet.feature.profile.ui.FeedbackViewModel
import com.asl.wallet.feature.profile.ui.Loadable
import com.asl.wallet.feature.profile.ui.ProfileViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ProfileMappingTest {
    @Test fun `profile is masked and the CR-LF address is put on one line (UAT shape)`() {
        val d = ProfileDto(regno = 6044, fname = "Ajay", lname = "Kumar", phone = "9798143077", email = "ajay.asltech@gmail.com",
            company_name = "ASL Payzones", panno = "IRAPK5045N", addsdt = "Bihar\r\nsaran Temple", pincode = "827012").toDetails()
        assertThat(d.name).isEqualTo("Ajay Kumar")
        assertThat(d.initials).isEqualTo("AK")
        assertThat(d.mobileMasked).isEqualTo("97XXX XX077")
        assertThat(d.emailMasked).isEqualTo("aj•••@gmail.com")
        assertThat(d.panMasked).isEqualTo("IRAXXXXX5N")
        assertThat(d.address).isEqualTo("Bihar, saran Temple")
        assertThat(d.city).isEmpty()
        assertThat(ProfileDto().toDetails().name).isEqualTo("Retailer")
    }

    @Test fun `commission keeps active rows, marks charges, drops empty groups`() {
        val c = CommissionDto(
            prepaidServices = listOf(
                PrepaidGroupDto(1, "Prepaid", listOf(OperatorDto("Jio", "2.50 %", status = 1), OperatorDto("Off op", "1 %", status = 0))),
                PrepaidGroupDto(2, "DTH", listOf(OperatorDto("Tata Play", null))),
            ),
            bankingServices = listOf(
                BankingGroupDto(500, "AEPS Cash Withdrawal", "COMMISSION", listOf(SlabDto("100 - 1000", "1.50 Rs."), SlabDto("1001 - 3000", "5.00 Rs."))),
                BankingGroupDto(600, "DMT", "CHARGES", listOf(SlabDto(null, "0.40 %"))),
            ),
        ).toCommission()
        assertThat(c.prepaid.map { it.title }).containsExactly("Prepaid")
        assertThat(c.prepaid.single().rows.map { it.label }).containsExactly("Jio")
        assertThat(c.banking[0].rows.map { it.value }).containsExactly("1.50 Rs.", "5.00 Rs.").inOrder()
        assertThat(c.banking[1].isCharge).isTrue()
        assertThat(c.banking[1].rows.single().label).isEqualTo("All amounts")
    }

    @Test fun `active services read every status spelling`() {
        assertThat(ServiceStatusDto(services_name = "AEPS", status = JsonPrimitive(1)).toActive()?.active).isTrue()
        assertThat(ServiceStatusDto(services_name = "UPI", status = JsonPrimitive("0")).toActive()?.active).isFalse()
        assertThat(ServiceStatusDto(services_name = "DMT", status = JsonPrimitive(false)).toActive()?.active).isFalse()
        assertThat(ServiceStatusDto(services_name = " ").toActive()).isNull()
    }
}

class ProfileRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: ProfileRepositoryImpl

    @Before fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(ProfileApi::class.java)
        repo = ProfileRepositoryImpl(api, json)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `me ignores the sensitive fields the server sends`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":true,"data":{"regno":6044,"fname":"Ajay","lname":"Kumar","phone":"9798143077",
            "aadharno":"234567890124","userpass":"${'$'}2b${'$'}hash","userauth":"secret","panno":"IRAPK5045N","company_name":"ASL Payzones"}}"""))
        val d = (repo.profile() as NetworkResult.Success).data
        assertThat(d.toString()).doesNotContain("234567890124")
        assertThat(d.toString()).doesNotContain("secret")
        assertThat(server.takeRequest().path).isEqualTo("/merchant/auth/me")
    }

    @Test fun `commission 500 with Prisma text is a failure that shows nothing internal`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"status":false,"statusCode":500,"message":"\nInvalid `prisma.apiOperator.findMany()` invocation","error":{"code":"INTERNAL_SERVER_ERROR"}}"""))
        val r = repo.commission()
        assertThat(r).isInstanceOf(NetworkResult.Failure.Api::class.java)
    }

    @Test fun `change password sends current, new and confirm, and returns where the code went`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":true,"data":{"sentTo":"aj***@gmail.com","channel":"EMAIL","expiresIn":"10 minutes"}}"""))
        val r = repo.requestPasswordChange("Old@1234", "New@12345") as NetworkResult.Success
        assertThat(r.data).isEqualTo(OtpTarget("aj***@gmail.com", "EMAIL"))
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/merchant/auth/change-password/request-otp")
        assertThat(req.body.readUtf8()).isEqualTo("""{"old_password":"Old@1234","new_password":"New@12345","confirm_password":"New@12345"}""")

        server.enqueue(MockResponse().setBody("""{"status":true,"message":"Password updated","data":null}"""))
        assertThat(repo.confirmPasswordChange("123456")).isInstanceOf(NetworkResult.Success::class.java)
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"otp":"123456"}""")
    }
}

// ---------------------------------------------------------------- ViewModels

private class FakeProfileRepo : ProfileRepository {
    var profileResult: NetworkResult<ProfileDetails> = NetworkResult.Failure.NoInternet
    var requestResult: NetworkResult<OtpTarget> = NetworkResult.Success(OtpTarget("aj•••@gmail.com", "EMAIL"), null)
    val confirmResults = ArrayDeque<NetworkResult<Unit>>()
    var lastRequest: Pair<String, String>? = null
    override suspend fun profile() = profileResult
    override suspend fun commission(): NetworkResult<Commission> = NetworkResult.Failure.NoInternet
    override suspend fun activeServices(): NetworkResult<List<ActiveService>> = NetworkResult.Success(emptyList(), null)
    override suspend fun requestPasswordChange(current: String, new: String): NetworkResult<OtpTarget> { lastRequest = current to new; return requestResult }
    override suspend fun confirmPasswordChange(otp: String) = confirmResults.removeFirstOrNull() ?: NetworkResult.Success(Unit, null)
}

private class FakeUser : CurrentUser {
    var loggedOut = false
    override val user: StateFlow<UserSummary?> = MutableStateFlow(UserSummary("Ajay Kumar", "ASL Payzones", 6044, "Retailer", false))
    override suspend fun logout() { loggedOut = true }
}

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeProfileRepo()

    @Test fun `offline shows a retryable failure, then loads`() = runTest {
        val vm = ProfileViewModel(repo, FakeUser())
        runCurrent()
        assertThat(vm.state.value).isEqualTo(Loadable.Failed(UiError.NoInternet))
        repo.profileResult = NetworkResult.Success(ProfileDto(fname = "Ajay").toDetails(), null)
        vm.load(); runCurrent()
        assertThat((vm.state.value as Loadable.Ready).data.name).isEqualTo("Ajay")
    }

    @Test fun `logout goes through the session owner`() = runTest {
        val u = FakeUser()
        ProfileViewModel(repo, u).logout(); runCurrent()
        assertThat(u.loggedOut).isTrue()
    }

    @Test fun `change password rules - strong, different, confirmed`() = runTest {
        val vm = ChangePasswordViewModel(repo)
        vm.onCurrent("Old@1234"); vm.onNew("short"); vm.onConfirm("short")
        assertThat(vm.state.value.canRequest).isFalse()
        vm.onNew("Old@1234"); vm.onConfirm("Old@1234")
        assertThat(vm.state.value.sameAsCurrent).isTrue()
        assertThat(vm.state.value.canRequest).isFalse()
        vm.onNew("New@12345"); vm.onConfirm("New@1234")
        assertThat(vm.state.value.confirmMismatch).isTrue()
        vm.onConfirm("New@12345")
        assertThat(vm.state.value.canRequest).isTrue()
    }

    @Test fun `OTP step - wrong code costs a try, right code changes and clears passwords`() = runTest {
        val vm = ChangePasswordViewModel(repo)
        vm.onCurrent("Old@1234"); vm.onNew("New@12345"); vm.onConfirm("New@12345")
        vm.requestOtp(); runCurrent()
        assertThat(vm.state.value.step).isEqualTo(ChangeStep.Otp)
        assertThat(vm.state.value.sentTo).isEqualTo("aj•••@gmail.com")
        assertThat(repo.lastRequest).isEqualTo("Old@1234" to "New@12345")

        repo.confirmResults.add(NetworkResult.Failure.Api(400, null, "Invalid OTP"))
        vm.onOtp("12a3456789")
        assertThat(vm.state.value.otp).isEqualTo("123456")
        vm.verify(); runCurrent()
        assertThat(vm.state.value.triesLeft).isEqualTo(ChangePasswordViewModel.OTP_TRIES - 1)
        assertThat(vm.state.value.otp).isEmpty()

        vm.events.test {
            vm.onOtp("654321"); vm.verify()
            assertThat(awaitItem()).isEqualTo(ChangePasswordEvent.Changed)
        }
        assertThat(vm.state.value.step).isEqualTo(ChangeStep.Done)
        assertThat(vm.state.value.current).isEmpty()
        assertThat(vm.state.value.new).isEmpty()
        advanceUntilIdle()
    }
}

class SupportViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `complaint needs a service and 10+ characters, and keeps the draft while the API is pending`() = runTest {
        val vm = ComplaintViewModel(PendingSupportRepository())
        vm.onDescription("too short")
        assertThat(vm.form.value.canSubmit).isFalse()
        vm.onService("AEPS"); vm.onDescription("Customer debited but receipt failed"); vm.onTransaction("ASL-AEPS-71KQ2 <script>")
        assertThat(vm.form.value.transactionId).isEqualTo("ASL-AEPS-71KQ2script")
        assertThat(vm.form.value.canSubmit).isTrue()
        vm.submit(); runCurrent()
        assertThat(vm.form.value.apiNotReady).isTrue()
        assertThat(vm.form.value.description).isEqualTo("Customer debited but receipt failed")
        vm.dismissApiNotReady()
        assertThat(vm.form.value.apiNotReady).isFalse()
    }

    @Test fun `attachments over 2 MB are refused`() = runTest {
        val vm = ComplaintViewModel(PendingSupportRepository())
        vm.onAttachment("big.jpg", ByteArray(ComplaintViewModel.MAX_ATTACHMENT + 1))
        assertThat(vm.form.value.attachmentName).isNull()
        vm.onAttachment("ok.jpg", ByteArray(10))
        assertThat(vm.form.value.attachmentName).isEqualTo("ok.jpg")
    }

    @Test fun `my complaints and feedback say the API is not ready`() = runTest {
        val c = ComplaintViewModel(PendingSupportRepository())
        c.loadList(); runCurrent()
        assertThat(c.list.value).isEqualTo(com.asl.wallet.feature.profile.domain.SupportResult.ApiNotReady)
        val f = FeedbackViewModel(PendingSupportRepository())
        assertThat(f.state.value.canSend).isFalse()
        f.onStars(4); f.toggleTag("Faster"); f.toggleTag("Faster"); f.toggleTag("Bug")
        assertThat(f.state.value.tags).containsExactly("Bug")
        f.send(); runCurrent()
        assertThat(f.state.value.apiNotReady).isTrue()
    }
}

class ServiceStatusTest {
    private fun svc(name: String, on: Boolean = true) = com.asl.wallet.feature.profile.domain.ActiveService(name, null, on)
    private fun state(list: List<com.asl.wallet.feature.profile.domain.ServiceEntry>, s: com.asl.wallet.feature.profile.domain.AppService) = list.first { it.service == s }.state

    @org.junit.Test fun `UAT services - AEPS, DMT and Recharge are active, Payout always, the rest not active`() {
        val list = com.asl.wallet.feature.profile.domain.ServiceStatus.entries(listOf(svc("AEPS (YES BANK)"), svc("DMT"), svc("Recharge"), svc("AEPS (KOTAK BANK)")))
        val A = com.asl.wallet.feature.profile.domain.ServiceState.Active
        val N = com.asl.wallet.feature.profile.domain.ServiceState.NotActive
        com.google.common.truth.Truth.assertThat(list).hasSize(com.asl.wallet.feature.profile.domain.AppService.entries.size)
        for (s in listOf(com.asl.wallet.feature.profile.domain.AppService.Aeps, com.asl.wallet.feature.profile.domain.AppService.MoneyTransfer,
            com.asl.wallet.feature.profile.domain.AppService.Payout, com.asl.wallet.feature.profile.domain.AppService.MobileRecharge,
            com.asl.wallet.feature.profile.domain.AppService.DthRecharge)) com.google.common.truth.Truth.assertThat(state(list, s)).isEqualTo(A)
        com.google.common.truth.Truth.assertThat(state(list, com.asl.wallet.feature.profile.domain.AppService.BillPayment)).isEqualTo(N)
        com.google.common.truth.Truth.assertThat(list.takeWhile { it.state == A }).hasSize(5) // active ones first
    }

    @org.junit.Test fun `switched on but not built is Coming soon, switched off by the server is Not active`() {
        val list = com.asl.wallet.feature.profile.domain.ServiceStatus.entries(listOf(svc("Micro ATM"), svc("BBPS", on = false), svc("Electricity Bill")))
        com.google.common.truth.Truth.assertThat(state(list, com.asl.wallet.feature.profile.domain.AppService.MicroAtm)).isEqualTo(com.asl.wallet.feature.profile.domain.ServiceState.ComingSoon)
        com.google.common.truth.Truth.assertThat(state(list, com.asl.wallet.feature.profile.domain.AppService.BillPayment)).isEqualTo(com.asl.wallet.feature.profile.domain.ServiceState.Active)
        com.google.common.truth.Truth.assertThat(state(list, com.asl.wallet.feature.profile.domain.AppService.Aeps)).isEqualTo(com.asl.wallet.feature.profile.domain.ServiceState.NotActive)
    }
}
