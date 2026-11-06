package com.asl.wallet.feature.aeps

import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.aeps.data.AepsApi
import com.asl.wallet.feature.aeps.data.AepsRepository
import com.asl.wallet.feature.aeps.data.AepsRepositoryImpl
import com.asl.wallet.feature.aeps.data.AepsSession
import com.asl.wallet.feature.aeps.data.AgentIdentity
import com.asl.wallet.feature.aeps.data.AgentIdentityApi
import com.asl.wallet.feature.aeps.data.BankDto
import com.asl.wallet.feature.aeps.data.DeviceDto
import com.asl.wallet.feature.aeps.data.InitDto
import com.asl.wallet.feature.aeps.data.LocationSource
import com.asl.wallet.feature.aeps.data.OnboardingPhotos
import com.asl.wallet.feature.aeps.data.OtpRef
import com.asl.wallet.feature.aeps.data.ProviderDto
import com.asl.wallet.feature.aeps.data.StatusDto
import com.asl.wallet.feature.aeps.data.TxnDto
import com.asl.wallet.feature.aeps.data.TxnInput
import com.asl.wallet.feature.aeps.data.ValidatedOtp
import com.asl.wallet.feature.aeps.data.maskDigits
import com.asl.wallet.feature.aeps.data.toConfig
import com.asl.wallet.feature.aeps.data.toLine
import com.asl.wallet.feature.aeps.data.toReceipt
import com.asl.wallet.feature.aeps.data.toStatus
import com.asl.wallet.feature.aeps.domain.AepsConfig
import com.asl.wallet.feature.aeps.domain.AepsProvider
import com.asl.wallet.feature.aeps.domain.AgentPrefill
import com.asl.wallet.feature.aeps.domain.AgentStatus
import com.asl.wallet.feature.aeps.domain.CaptureType
import com.asl.wallet.feature.aeps.domain.CustomerBank
import com.asl.wallet.feature.aeps.domain.GeoPoint
import com.asl.wallet.feature.aeps.domain.NextStep
import com.asl.wallet.feature.aeps.domain.OnboardingForm
import com.asl.wallet.feature.aeps.domain.Pid
import com.asl.wallet.feature.aeps.domain.TxnOutcome
import com.asl.wallet.feature.aeps.domain.TxnType
import com.asl.wallet.feature.aeps.domain.nextStep
import com.asl.wallet.feature.aeps.rd.CaptureResult
import com.asl.wallet.feature.aeps.ui.AepsTransactionViewModel
import com.asl.wallet.feature.aeps.ui.TxnPhase
import com.asl.wallet.feature.aeps.ui.formatDob
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

/** A real Aadhaar-format number that passes Verhoeff (test value, not a person's). */
private const val AADHAAR = "234123412346"

private fun status(ekyc: Boolean = true, bank: Boolean = true, bio: Boolean = true, login: Boolean = true) =
    AgentStatus(ekyc, bank, bio, login, "KT-55820", null, null, AgentPrefill())

class AepsRulesTest {
    @Test fun `next step follows the backend order for each bank`() {
        val k = AepsProvider.Kotak; val y = AepsProvider.YesBank
        assertThat(status(ekyc = false).nextStep(k)).isEqualTo(NextStep.Onboarding)
        assertThat(status(bank = false).nextStep(k)).isEqualTo(NextStep.BankVerification)
        assertThat(status(bank = false).nextStep(y)).isEqualTo(NextStep.Ready)          // Yes Bank has no bank step
        assertThat(status(bio = false).nextStep(y)).isEqualTo(NextStep.BiometricKyc)
        assertThat(status(bio = false).nextStep(k)).isEqualTo(NextStep.Ready)           // Kotak has no bio step
        assertThat(status(login = false).nextStep(k)).isEqualTo(NextStep.DailyLogin)
        assertThat(status().nextStep(y)).isEqualTo(NextStep.Ready)
    }

    @Test fun `deposit only for Yes Bank, no Aadhaar Pay, OTP only above 5000 withdrawal`() {
        assertThat(TxnType.forProvider(AepsProvider.Kotak)).containsExactly(TxnType.Withdraw, TxnType.Balance, TxnType.Statement).inOrder()
        assertThat(TxnType.forProvider(AepsProvider.YesBank)).contains(TxnType.Deposit)
        assertThat(TxnType.entries.map { it.code }).doesNotContain("ADHPAY")
        assertThat(TxnType.Withdraw.needsCustomerOtp(BigDecimal(5000))).isFalse()
        assertThat(TxnType.Withdraw.needsCustomerOtp(BigDecimal(5001))).isTrue()
        assertThat(TxnType.Deposit.needsCustomerOtp(BigDecimal(9000))).isFalse()
    }

    @Test fun `PID options carry WADH and OTP safely, and the PID block is parsed`() {
        val o = Pid.fingerOptions(wadh = "ab\"c", otp = "12 34x")
        assertThat(o).contains("fType=\"2\"")
        assertThat(o).contains("wadh=\"ab&quot;c\"")
        assertThat(o).contains("otp=\"1234\"")
        assertThat(Pid.faceOptions("123", "w")).contains("name=\"txnId\" value=\"123\"")

        val ok = """<PidData><Resp errCode="0" errInfo="Success" fCount="1"/><DeviceInfo/><Data type="X">abc</Data></PidData>"""
        assertThat(Pid.parse(ok)).isEqualTo(Pid.Parsed.Ok(ok))
        assertThat(Pid.parse("""<PidData><Resp errCode="700" errInfo="Capture timed out"/></PidData>""")).isEqualTo(Pid.Parsed.Error("700", "Capture timed out"))
        assertThat(Pid.parse("<html>nope</html>")).isEqualTo(Pid.Parsed.Invalid)
        assertThat(Pid.parse(null)).isEqualTo(Pid.Parsed.Invalid)
    }

    @Test fun `onboarding prefill uses the bank's data first, then the ASL profile`() {
        val profile = com.asl.wallet.feature.aeps.domain.ProfilePrefill("Ajay", "Kumar", "a@b.co", "9798143077", "IRAPK5045N", "Bihar, saran Temple", "", "827012", "ASL Payzones")
        val f = OnboardingForm.from(AgentPrefill(firstName = "AJAY K", ifsc = "SBIN0001234"), profile)
        assertThat(f.firstName).isEqualTo("AJAY K")
        assertThat(f.lastName).isEqualTo("Kumar")
        assertThat(f.shopName).isEqualTo("ASL Payzones")
        assertThat(f.holderName).isEqualTo("Ajay Kumar")
        assertThat(f.pincode).isEqualTo("827012")
        assertThat(f.ifsc).isEqualTo("SBIN0001234")
        assertThat(f.pan).isEqualTo("IRAPK5045N")
    }

    @Test fun `onboarding form mirrors the backend rules`() {
        val good = OnboardingForm(
            firstName = "Ajay", lastName = "Kumar", email = "a@b.co", phone = "9798143077", dob = "12/05/1990",
            address = "Main Road", city = "Habra", state = "West Bengal", pincode = "743263", aadhaar = AADHAAR, pan = "ABCPK1234K",
            bankAccount = "302918444417", ifsc = "SBIN0001234", bankName = "SBI", holderName = "Ajay Kumar",
            shopName = "ASL Payzones", shopPan = "ABCPK1234K", shopAddress = "Main Road", shopCity = "Habra",
            shopDistrict = "North 24 Parganas", shopState = "West Bengal", shopPincode = "743263",
        )
        assertThat(good.errors()).isEmpty()
        assertThat(good.isoDob()).isEqualTo("1990-05-12")
        assertThat(good.copy(aadhaar = "234123412345").errors()).containsExactly(OnboardingForm.Field.Aadhaar) // Verhoeff fails
        assertThat(good.copy(dob = "31/02/1990").errors()).containsExactly(OnboardingForm.Field.Dob)
        assertThat(good.copy(dob = "01/01/2020").errors()).containsExactly(OnboardingForm.Field.Dob)          // under 18
        assertThat(good.copy(pan = "ABC123").errors()).containsExactly(OnboardingForm.Field.Pan)
        val f = good.fields(AepsProvider.Kotak, GeoPoint(22.5, 88.3))
        assertThat(f.keys).containsAtLeast("firstName", "aadharNo", "panNo", "bankIfsc", "shopPan", "shopPincode", "providerId", "latitude", "longitude", "dob")
        assertThat(f["dob"]).isEqualTo("1990-05-12")
        assertThat(f["providerId"]).isEqualTo("2")
        assertThat(f["latitude"]).isEqualTo("22.5000000")
        assertThat(formatDob("12051990")).isEqualTo("12/05/1990")
        assertThat(formatDob("120")).isEqualTo("12/0")
    }
}

class AepsMappingTest {
    @Test fun `init drops the padded blank banks and maps devices to RD services (UAT shape)`() {
        val c = InitDto(
            deviceInfo = listOf(DeviceDto("FaceAuth", "8", JsonPrimitive(1)), DeviceDto("Mantra L1", "5", JsonPrimitive(1)), DeviceDto("Unknown X", "9", JsonPrimitive(1)), DeviceDto("Morpho L1", "6", JsonPrimitive(0))),
            providerInfo = listOf(ProviderDto("Yes Bank", JsonPrimitive("1"), JsonPrimitive(1)), ProviderDto("Kotak Bank", JsonPrimitive("2"), JsonPrimitive(1))),
            bankList = listOf(BankDto("                    ", "        "), BankDto("  State Bank of India ", "607094"), BankDto("Axis  Bank", "607153"), BankDto("Dup", "607094")),
            allowedAmount = listOf(JsonPrimitive(500), JsonPrimitive("1000"), JsonPrimitive(0)),
        ).toConfig()
        assertThat(c.banks.map { it.name }).containsExactly("Axis Bank", "State Bank of India").inOrder()
        assertThat(c.devices.map { it.packageName }).containsExactly("in.gov.uidai.facerd", "com.mantra.mfs110.rdservice").inOrder()
        assertThat(c.devices.first().isFace).isTrue()
        assertThat(c.providers.map { it.provider }).containsExactly(AepsProvider.YesBank, AepsProvider.Kotak)
        assertThat(c.quickAmounts).containsExactly(500L, 1000L).inOrder()
    }

    @Test fun `status reads an empty or a filled bcAgentData`() {
        val json = Json { ignoreUnknownKeys = true }
        val empty = StatusDto(isEkycDone = false, outletid = " 55820 ", bcAgentData = buildJsonObject { }).toStatus(json)
        assertThat(empty.ekycDone).isFalse()
        assertThat(empty.outletId).isEqualTo("55820")
        val filled = StatusDto(isEkycDone = true, bcAgentData = buildJsonObject { put("fname", "Ajay"); put("ifsccode", "SBIN0001234"); put("aadhaar", AADHAAR) }).toStatus(json)
        assertThat(filled.prefill.firstName).isEqualTo("Ajay")
        assertThat(filled.prefill.ifsc).isEqualTo("SBIN0001234")
        assertThat(filled.prefill.toString()).doesNotContain(AADHAAR)
    }

    @Test fun `receipts are masked and never use the Aadhaar the server echoes back`() {
        val req = TxnInput(AepsProvider.Kotak, TxnType.Statement, AADHAAR, "9876543210", CustomerBank("607094", "State Bank of India"), null, CaptureType.FINGER, "<PidData/>", GeoPoint(1.0, 2.0))
        val r = TxnDto(aepsmode = "Mini Statement", txnReferenceId = "330719004812", bankAccountBalance = "12480.50", bank = "",
            miniStatementData = listOf(
                buildJsonObject { put("date", "02/11"); put("txnType", "Cr"); put("amount", "1500.00"); put("narration", "UPI/CR/302918444417/ANIL") },
                buildJsonObject { put("date", "01/11"); put("txnType", "Dr"); put("amount", "2000.00"); put("narration", "ATM WDL") },
                JsonPrimitive("junk"),
            )).toReceipt(req, "ok")
        assertThat(r.customerAadhaarMasked).isEqualTo("XXXX XXXX 2346")
        assertThat(r.customerMobileMasked).isEqualTo("98XXX XX210")
        assertThat(r.bankName).isEqualTo("State Bank of India")
        assertThat(r.statement).hasSize(2)
        assertThat(r.statement[0].narration).isEqualTo("UPI/CR/••••4417/ANIL")
        assertThat(r.statement[0].isCredit).isTrue()
        assertThat(r.statement[1].isCredit).isFalse()
        assertThat(r.toString()).doesNotContain(AADHAAR)
        assertThat(maskDigits("A/C 0185104000179539 ok 12345")).isEqualTo("A/C ••••9539 ok 12345")
        assertThat(JsonPrimitive("x").toLine()).isNull()
    }
}

class AepsRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val session = AepsSession()
    private lateinit var repo: AepsRepositoryImpl

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = AepsRepositoryImpl(retrofit.create(AepsApi::class.java), retrofit.create(AgentIdentityApi::class.java), session, json)
    }
    @After fun tearDown() = server.shutdown()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    private val bank = CustomerBank("607094", "State Bank of India")
    private fun txn(type: TxnType = TxnType.Withdraw) =
        TxnInput(AepsProvider.Kotak, type, AADHAAR, "9876543210", bank, BigDecimal(2000), CaptureType.FINGER, "<PidData>x</PidData>", GeoPoint(22.5, 88.3))

    @Test fun `status uses the retailer's own Aadhaar from me, kept in memory only`() = runTest {
        respond(200, """{"status":true,"data":{"aadharno":"$AADHAAR","phone":"9798143077","email":"a@b.co","panno":"irapk5045n","userpass":"hash"}}""")
        respond(200, """{"status":true,"message":"E-KYC pending","data":{"isEkycDone":false,"isBankEkycDone":false,"isBioKyc":false,"isTwoFA":false,"providerId":2,"outletid":"","bcAgentData":{},"stateList":{}}}""")
        val s = repo.status(AepsProvider.Kotak) as NetworkResult.Success
        assertThat(s.data.nextStep(AepsProvider.Kotak)).isEqualTo(NextStep.Onboarding)
        assertThat(server.takeRequest().path).isEqualTo("/merchant/auth/me")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"providerId":2,"aadhaarNo":"$AADHAAR","mobileNo":"9798143077"}""")
        assertThat(session.identity?.pan).isEqualTo("IRAPK5045N")
        assertThat(session.status).isNull() // the read-only check doesn't start a flow
    }

    @Test fun `missing Aadhaar in the profile is a clear error`() = runTest {
        respond(200, """{"status":true,"data":{"aadharno":null,"phone":"9798143077"}}""")
        val r = repo.status(AepsProvider.Kotak) as NetworkResult.Failure.Api
        assertThat(r.message).isEqualTo(AepsRepositoryImpl.MISSING_IDENTITY)
    }

    @Test fun `transaction sends an idempotency key and the PID, and reads the receipt`() = runTest {
        respond(200, """{"status":true,"message":"Success","data":{"aepsmode":"Cash Withdrawal","timestamp":"2026-11-04 09:52:00","txnReferenceId":"330719004877",
            "bankAccountBalance":"10480.50","bank":"State Bank of India","aadhaarNumber":"$AADHAAR","transactionAmount":"2000","miniStatementData":[]}}""")
        val t = txn()
        val o = repo.transact(t) as TxnOutcome.Success
        assertThat(o.receipt.bankRef).isEqualTo("330719004877")
        assertThat(o.receipt.amount).isEqualTo(BigDecimal("2000"))
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/merchant/aeps/transaction")
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo(t.idempotencyKey.value)
        val body = req.body.readUtf8()
        assertThat(body).contains("\"txnType\":\"CW\"")
        assertThat(body).contains("\"bankiin\":\"607094\"")
        assertThat(body).contains("\"biometricData\":\"<PidData>x</PidData>\"")
        assertThat(body).contains("\"latitude\":\"22.5000000\"")
    }

    @Test fun `a 5xx or a timeout is Pending, never Failed`() = runTest {
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.transact(txn())).isEqualTo(TxnOutcome.Pending)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThat(repo.transact(txn())).isEqualTo(TxnOutcome.Pending)
    }

    @Test fun `bank decline is Failed with a safe message, Yes Bank stub is ProviderUnavailable`() = runTest {
        respond(400, """{"status":false,"statusCode":400,"message":"Biometric data did not match"}""")
        assertThat(repo.transact(txn())).isEqualTo(TxnOutcome.Failed("Biometric data did not match"))
        respond(400, """{"status":false,"statusCode":400,"message":"Invalid `prisma.aeps` invocation"}""")
        assertThat(repo.transact(txn())).isEqualTo(TxnOutcome.Failed(null))
        respond(400, """{"status":false,"message":"InstantPay AEPS transaction provider is not implemented yet [PROV001]"}""")
        assertThat(repo.transact(txn().copy(provider = AepsProvider.YesBank))).isEqualTo(TxnOutcome.ProviderUnavailable)
    }

    @Test fun `customer OTP without the bank reference (current backend) is an error, not a silent continue`() = runTest {
        respond(200, """{"status":true,"message":"OTP Successfully sent","data":{}}""")
        val r = repo.customerOtp(AepsProvider.Kotak, TxnType.Withdraw, AADHAAR, "9876543210", bank, BigDecimal(8000), GeoPoint(1.0, 2.0))
        assertThat((r as NetworkResult.Failure.Api).message).isEqualTo(AepsRepositoryImpl.OTP_REF_MISSING)
        respond(200, """{"status":true,"data":{"fpTransactionId":"FP123"}}""")
        assertThat((repo.customerOtp(AepsProvider.Kotak, TxnType.Withdraw, AADHAAR, "9876543210", bank, BigDecimal(8000), GeoPoint(1.0, 2.0)) as NetworkResult.Success).data).isEqualTo("FP123")
    }

    @Test fun `biometric calls succeed with empty data (no cast crash)`() = runTest {
        session.identity = AgentIdentity(AADHAAR, "9798143077", "", "ABCPK1234K")
        respond(200, """{"status":true,"message":"Done","data":null}""")
        assertThat(repo.dailyLogin(AepsProvider.Kotak, CaptureType.FINGER, "<PidData/>", GeoPoint(1.0, 2.0))).isInstanceOf(NetworkResult.Success::class.java)
        respond(200, """{"status":true,"message":"Done","data":{"x":1}}""")
        assertThat(repo.onboardingBiometric(AepsProvider.Kotak, AADHAAR, "M1", ValidatedOtp("1", "h", null, null), CaptureType.FINGER, "<PidData/>", GeoPoint(1.0, 2.0)))
            .isInstanceOf(NetworkResult.Success::class.java)
    }
}

// ---------------------------------------------------------------- transaction ViewModel

private class FakeLocation(var point: GeoPoint? = GeoPoint(22.5, 88.3)) : LocationSource {
    override fun hasPermission() = true
    override fun isEnabled() = true
    override suspend fun current() = point
}

private class FakeAepsRepo : AepsRepository {
    val txns = mutableListOf<TxnInput>()
    var outcome: TxnOutcome = TxnOutcome.Pending
    var otpCalls = 0
    override suspend fun config() = NetworkResult.Success(AepsConfig(emptyList(), emptyList(), emptyList(), emptyList()), null)
    override suspend fun identity() = NetworkResult.Success(AgentIdentity(AADHAAR, "9798143077", "", ""), null)
    override suspend fun status(provider: AepsProvider) = NetworkResult.Success(status(), null)
    override suspend fun onboard(provider: AepsProvider, form: OnboardingForm, photos: OnboardingPhotos, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun resendOnboardingOtp(provider: AepsProvider, form: OnboardingForm, merchantLoginId: String, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun validateOnboardingOtp(provider: AepsProvider, otp: String, ref: OtpRef) = NetworkResult.Failure.NoInternet
    override suspend fun onboardingBiometric(provider: AepsProvider, aadhaar: String, merchantLoginId: String?, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun bankSendOtp(provider: AepsProvider, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun bankValidateOtp(provider: AepsProvider, otp: String, ref: OtpRef) = NetworkResult.Failure.NoInternet
    override suspend fun bankBiometric(provider: AepsProvider, v: ValidatedOtp, capture: CaptureType, pid: String, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun biometricKyc(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun dailyLogin(provider: AepsProvider, capture: CaptureType, pid: String, at: GeoPoint) = NetworkResult.Failure.NoInternet
    override suspend fun customerOtp(provider: AepsProvider, type: TxnType, aadhaar: String, mobile: String, bank: CustomerBank, amount: BigDecimal, at: GeoPoint): NetworkResult<String> { otpCalls++; return NetworkResult.Success("FP9", null) }
    override suspend fun transact(request: TxnInput): TxnOutcome { txns += request; return outcome }
}

class AepsTransactionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeAepsRepo()
    private val session = AepsSession().apply { provider = AepsProvider.Kotak }
    private val bank = CustomerBank("607094", "State Bank of India")

    private fun filled(vm: AepsTransactionViewModel, amount: String = "2000") {
        vm.onAadhaar(AADHAAR); vm.onMobile("98765 43210"); vm.onBank(bank); vm.onAmount(amount)
    }

    @Test fun `scan needs valid Aadhaar, mobile, bank and a whole-rupee amount in range`() = runTest {
        val vm = AepsTransactionViewModel(repo, session, FakeLocation())
        assertThat(vm.onScanPressed()).isFalse()
        assertThat(vm.state.value.showErrors).isTrue()
        filled(vm, "50")
        assertThat(vm.state.value.amountOk).isFalse()
        vm.onAmount("10001"); assertThat(vm.state.value.amountOk).isFalse()
        vm.onAmount("2000"); assertThat(vm.onScanPressed()).isTrue()
        vm.selectType(TxnType.Balance)
        assertThat(vm.state.value.amountText).isEmpty()
        assertThat(vm.onScanPressed()).isTrue()
    }

    @Test fun `above 5000 asks for the customer OTP first and puts it in the scan`() = runTest {
        val vm = AepsTransactionViewModel(repo, session, FakeLocation())
        filled(vm, "8000")
        assertThat(vm.onScanPressed()).isFalse()
        runCurrent()
        assertThat(repo.otpCalls).isEqualTo(1)
        assertThat(vm.state.value.phase).isEqualTo(TxnPhase.CustomerOtp)
        vm.onOtp("123456")
        assertThat(vm.otpEntered()).isTrue()
        assertThat(vm.scanOtp).isEqualTo("123456")
        vm.onCapture(CaptureResult.Ok("<PidData/>", CaptureType.FINGER)); runCurrent()
        assertThat(repo.txns.single().otpTransactionId).isEqualTo("FP9")
    }

    @Test fun `pending keeps no customer for repeat, success does, and each new attempt gets a new key`() = runTest {
        val vm = AepsTransactionViewModel(repo, session, FakeLocation())
        filled(vm)
        vm.onCapture(CaptureResult.Ok("<PidData/>", CaptureType.FINGER)); runCurrent()
        assertThat(vm.state.value.outcome).isEqualTo(TxnOutcome.Pending)
        assertThat(session.lastCustomer).isNull()

        repo.outcome = TxnOutcome.Failed("declined")
        vm.again(); vm.onAmount("2000")
        vm.onCapture(CaptureResult.Ok("<PidData/>", CaptureType.FINGER)); runCurrent()
        assertThat(repo.txns.map { it.idempotencyKey }.distinct()).hasSize(2)
        assertThat(vm.state.value.aadhaar).isEqualTo(AADHAAR) // "same customer" kept in memory
    }

    @Test fun `no location means no transaction`() = runTest {
        val vm = AepsTransactionViewModel(repo, session, FakeLocation(point = null))
        filled(vm)
        vm.onCapture(CaptureResult.Ok("<PidData/>", CaptureType.FINGER)); runCurrent()
        assertThat(repo.txns).isEmpty()
        assertThat(vm.state.value.phase).isEqualTo(TxnPhase.Form)
        assertThat(vm.state.value.error).isNotNull()
    }

    @Test fun `bank search puts prefix matches first`() {
        val all = listOf(CustomerBank("1", "Bank of India"), CustomerBank("2", "State Bank of India"), CustomerBank("3", "Indian Bank"))
        assertThat(AepsTransactionViewModel.search(all, "ind").map { it.name }).containsExactly("Indian Bank", "Bank of India", "State Bank of India").inOrder()
        assertThat(AepsTransactionViewModel.search(all, "")).isEqualTo(all)
    }
}
