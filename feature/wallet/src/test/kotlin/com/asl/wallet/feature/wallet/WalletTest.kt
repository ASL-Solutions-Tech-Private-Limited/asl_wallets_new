package com.asl.wallet.feature.wallet

import androidx.lifecycle.SavedStateHandle
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.wallet.data.BankAccountDto
import com.asl.wallet.feature.wallet.data.FundRequestRowDto
import com.asl.wallet.feature.wallet.data.LedgerDto
import com.asl.wallet.feature.wallet.data.LimitsDto
import com.asl.wallet.feature.wallet.data.OrderResult
import com.asl.wallet.feature.wallet.data.PayUFields
import com.asl.wallet.feature.wallet.data.RequestFromDto
import com.asl.wallet.feature.wallet.data.WalletApi
import com.asl.wallet.feature.wallet.data.WalletRepository
import com.asl.wallet.feature.wallet.data.WalletRepositoryImpl
import com.asl.wallet.feature.wallet.data.WalletSession
import com.asl.wallet.feature.wallet.data.adminOf
import com.asl.wallet.feature.wallet.data.summaryOf
import com.asl.wallet.feature.wallet.data.toAccount
import com.asl.wallet.feature.wallet.data.toEntry
import com.asl.wallet.feature.wallet.data.toItem
import com.asl.wallet.feature.wallet.data.toLimits
import com.asl.wallet.feature.wallet.domain.AccountOwner
import com.asl.wallet.feature.wallet.domain.AddBankForm
import com.asl.wallet.feature.wallet.domain.AddMoneyInfo
import com.asl.wallet.feature.wallet.domain.AddMoneyResult
import com.asl.wallet.feature.wallet.domain.AddMoneyStatus
import com.asl.wallet.feature.wallet.domain.AmountProblem
import com.asl.wallet.feature.wallet.domain.BankAccount
import com.asl.wallet.feature.wallet.domain.BankOption
import com.asl.wallet.feature.wallet.domain.CompanyBank
import com.asl.wallet.feature.wallet.domain.Day
import com.asl.wallet.feature.wallet.domain.FundMode
import com.asl.wallet.feature.wallet.domain.FundRequestForm
import com.asl.wallet.feature.wallet.domain.FundRequestItem
import com.asl.wallet.feature.wallet.domain.FundRequestPage
import com.asl.wallet.feature.wallet.domain.FundRequestSetup
import com.asl.wallet.feature.wallet.domain.FundStatus
import com.asl.wallet.feature.wallet.domain.Limits
import com.asl.wallet.feature.wallet.domain.PayUOrder
import com.asl.wallet.feature.wallet.domain.SendAccount
import com.asl.wallet.feature.wallet.domain.SendMoneySetup
import com.asl.wallet.feature.wallet.domain.SendOutcome
import com.asl.wallet.feature.wallet.domain.WalletEntry
import com.asl.wallet.feature.wallet.domain.WalletOverview
import com.asl.wallet.feature.wallet.domain.searchBanks
import com.asl.wallet.feature.wallet.payu.PayUCheckout
import com.asl.wallet.feature.wallet.payu.PayUReturn
import com.asl.wallet.feature.wallet.payu.WalletConfig
import com.asl.wallet.feature.wallet.ui.AddMoneyViewModel
import com.asl.wallet.feature.wallet.ui.AddPhase
import com.asl.wallet.feature.wallet.ui.FundPhase
import com.asl.wallet.feature.wallet.ui.FundRequestViewModel
import com.asl.wallet.feature.wallet.ui.SendMoneyViewModel
import com.asl.wallet.feature.wallet.ui.SendPhase
import com.asl.wallet.feature.wallet.ui.WalletViewModel
import com.asl.wallet.feature.wallet.ui.cleanAmount
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

// ---------------------------------------------------------------- domain rules

class WalletDomainTest {
    private val limits = Limits(BigDecimal(500), BigDecimal(2000))

    @Test fun `limits come from the server and are checked in the app`() {
        assertThat(limits.check(null)).isEqualTo(AmountProblem.Empty)
        assertThat(limits.check(BigDecimal(499))).isEqualTo(AmountProblem.BelowMin)
        assertThat(limits.check(BigDecimal("2000.01"))).isEqualTo(AmountProblem.AboveMax)
        assertThat(limits.check(BigDecimal(1000))).isNull()
        assertThat(limits.check(BigDecimal(1000), balance = BigDecimal(999))).isEqualTo(AmountProblem.OverBalance)
        assertThat(limits.quickAmounts()).containsExactly(500L, 1000L, 2000L).inOrder()
        assertThat(Limits(BigDecimal(10), BigDecimal(50000)).quickAmounts()).containsExactly(10000L, 25000L, 50000L).inOrder()
        assertThat(Limits(BigDecimal(1), BigDecimal(2000)).quickAmounts()).containsExactly(500L, 1000L, 2000L).inOrder() // UAT limits on 5 Nov
    }

    @Test fun `missing or broken server limits fall back, never zero`() {
        val fb = Limits(BigDecimal(1), BigDecimal(2))
        assertThat(LimitsDto(JsonPrimitive(500), JsonPrimitive("2,000")).toLimits(fb)).isEqualTo(Limits(BigDecimal(500), BigDecimal(2000)))
        assertThat(LimitsDto(JsonPrimitive(0), JsonPrimitive(10)).toLimits(fb)).isEqualTo(fb)
        assertThat(LimitsDto(JsonPrimitive(900), JsonPrimitive(10)).toLimits(fb)).isEqualTo(fb)
        assertThat(null.toLimits(fb)).isEqualTo(fb)
    }

    @Test fun `add money is credited only on a server SUCCESS, anything unknown is pending`() {
        fun of(s: String?, c: Boolean? = null) = AddMoneyStatus.of(s, c, "T1", BigDecimal(1000), null, null, null)
        assertThat(of("SUCCESS")).isInstanceOf(AddMoneyResult.Success::class.java)
        assertThat(of("success", c = false)).isInstanceOf(AddMoneyResult.Pending::class.java)
        assertThat(of("INITIATED")).isInstanceOf(AddMoneyResult.Pending::class.java)
        assertThat(of(null)).isInstanceOf(AddMoneyResult.Pending::class.java)
        assertThat(of("user cancelled")).isInstanceOf(AddMoneyResult.Failed::class.java)
        assertThat(of("FAILURE")).isInstanceOf(AddMoneyResult.Failed::class.java)
    }

    @Test fun `PayU order needs every SDK field from the server, otherwise opens soon`() {
        val flat = buildJsonObject { put("clientTxnId", "UPI1"); put("paymentUrl", "https://x"); put("status", "INITIATED") }
        assertThat(PayUFields.read(flat, BigDecimal(1000))).isEqualTo(OrderResult.NotSupportedYet)
        val nested = buildJsonObject {
            putJsonObject("payu") {
                listOf("key", "amount", "productinfo", "firstname", "email", "surl", "furl", "hash").forEach { put(it, "v-$it") }
                put("txnid", "ASL123"); put("udf1", "u1"); put("salt", JsonPrimitive("must-not-matter"))
            }
        }
        val r = PayUFields.read(nested, BigDecimal(1000)) as OrderResult.Ready
        assertThat(r.order.txnId).isEqualTo("ASL123")
        assertThat(r.order.params["udf1"]).isEqualTo("u1")
        assertThat(r.order.amount).isEqualTo(BigDecimal(1000)) // "v-amount" isn't a number, so the typed amount is used
    }

    @Test fun `fund request form - date not in future or older than 90 days, UTR shape, bank and mode`() {
        val today = Day(2026, 11, 4)
        val ok = FundRequestForm(CompanyBank(1, "UCO", "32840110063873", "UCBA0003284"), "5000", FundMode.Imps, "UTR2026110400123", today, "")
        assertThat(ok.problems(today)).isEmpty()
        assertThat(ok.copy(date = Day(2026, 11, 5)).problems(today)).containsExactly(FundRequestForm.Field.Date)
        assertThat(ok.copy(date = Day(2026, 8, 5)).problems(today)).containsExactly(FundRequestForm.Field.Date)
        assertThat(ok.copy(date = Day(2026, 8, 6)).problems(today)).isEmpty()
        assertThat(ok.copy(utr = "12").problems(today)).containsExactly(FundRequestForm.Field.Utr)
        assertThat(ok.copy(utr = "UTR 12<script>").problems(today)).containsExactly(FundRequestForm.Field.Utr)
        assertThat(FundRequestForm().problems(today)).containsExactly(
            FundRequestForm.Field.Bank, FundRequestForm.Field.Amount, FundRequestForm.Field.Mode, FundRequestForm.Field.Utr, FundRequestForm.Field.Date,
        )
    }

    @Test fun `day parses the server formats and counts days across months and leap years`() {
        assertThat(Day.parse("2026-11-04")).isEqualTo(Day(2026, 11, 4))
        assertThat(Day.parse("04-11-2026")).isEqualTo(Day(2026, 11, 4))
        assertThat(Day.parse("2026-11-04T10:15:00.000Z")).isEqualTo(Day(2026, 11, 4))
        assertThat(Day.parse("nope")).isNull()
        assertThat(Day(1970, 1, 1).epochDay).isEqualTo(0)
        assertThat(Day(2024, 3, 1).epochDay - Day(2024, 2, 28).epochDay).isEqualTo(2)
        assertThat(Day.ofEpochMillisUtc(Day(2026, 11, 4).epochDay * 86_400_000L)).isEqualTo(Day(2026, 11, 4))
        assertThat(Day(2026, 1, 9).iso()).isEqualTo("2026-01-09")
    }

    @Test fun `add bank account - numbers match, IFSC format, holder name and both photos`() {
        val ok = AddBankForm(BankOption("12", "UCO BANK"), "32840110063873", "32840110063873", "UCBA0003284", "AJAY KUMAR", AccountOwner.Self, 120_000, 200_000)
        assertThat(ok.problems()).isEmpty()
        assertThat(ok.copy(confirm = "32840110063874").problems()).containsExactly(AddBankForm.Field.Confirm)
        assertThat(ok.copy(ifsc = "UCBA1003284").problems()).containsExactly(AddBankForm.Field.Ifsc)
        assertThat(ok.copy(holder = "A").problems()).containsExactly(AddBankForm.Field.Holder)
        assertThat(ok.copy(panPhoto = 0, passbookPhoto = 0).problems()).containsExactly(AddBankForm.Field.PanPhoto, AddBankForm.Field.PassbookPhoto)
    }

    @Test fun `bank search puts names starting with the query first`() {
        val all = listOf(BankOption("1", "THE UDUPI COOPERATIVE TOWN BANK"), BankOption("2", "UCO BANK"), BankOption("3", "AXIS BANK"))
        assertThat(searchBanks(all, "uc").map { it.id }).containsExactly("2").inOrder()
        assertThat(searchBanks(all, "u").map { it.id }).containsExactly("2", "1").inOrder()
        assertThat(searchBanks(all, " ")).isEqualTo(all)
    }

    @Test fun `typed amounts keep at most 2 decimals and 8 digits`() {
        assertThat(cleanAmount("₹1,000.505")).isEqualTo("1000.50")
        assertThat(cleanAmount("123456789")).isEqualTo("12345678")
        assertThat(cleanAmount("1.2.3")).isEqualTo("1.23")
    }
}

// ---------------------------------------------------------------- response mapping

class WalletMappingTest {
    @Test fun `fund request status from flags or label, account to last 4`() {
        val base = FundRequestRowDto(id = 7, requestAmount = JsonPrimitive("5000.00"), tranId = "UTR1", accountNo = "32840110063873", status = "Pending")
        assertThat(base.toItem()!!.status).isEqualTo(FundStatus.Pending)
        assertThat(base.toItem()!!.ref).isEqualTo("FR-7")
        assertThat(base.toItem()!!.accountLast4).isEqualTo("3873")
        assertThat(base.copy(transferStatus = JsonPrimitive(1)).toItem()!!.status).isEqualTo(FundStatus.Approved)
        assertThat(base.copy(declineStatus = JsonPrimitive("1")).toItem()!!.status).isEqualTo(FundStatus.Declined)
        assertThat(base.copy(status = "Transferred").toItem()!!.status).isEqualTo(FundStatus.Approved)
        assertThat(base.copy(requestAmount = null).toItem()).isNull()
        assertThat(base.copy(id = null).toItem()).isNull()
    }

    @Test fun `UAT 5 Nov fund-request item - bank in bankDetails, Y and N flags, dash means none`() {
        val row = Json { ignoreUnknownKeys = true }.decodeFromString(FundRequestRowDto.serializer(), """{"id":130,"tranId":"APK-INJI","reqForm":"1","bankId":44,
            "bankDetails":{"bankName":"UCO Bank","accountNumber":"32840110063873","ifscCode":"UCBA0003284"},"amount":1000,"amountFormatted":"1000.00",
            "paymentMode":"UPI","depositDate":"03/11/2026","remarks":"","tranDate":"03/11/2026","tranTime":"06:51 PM","transferStatus":"Y","declineStatus":"N",
            "adminRemarks":"-","isPending":false,"statusLabel":"Approved"}""")
        val i = row.toItem()!!
        assertThat(i.status).isEqualTo(FundStatus.Approved)
        assertThat(i.bankName).isEqualTo("UCO Bank")
        assertThat(i.accountLast4).isEqualTo("3873")
        assertThat(i.adminRemarks).isNull()
        assertThat(i.mode).isEqualTo(FundMode.Upi)
        assertThat(Day.parse(i.depositDate)).isEqualTo(Day(2026, 11, 3))
        assertThat(row.copy(transferStatus = JsonPrimitive("N"), statusLabel = "Pending").toItem()!!.status).isEqualTo(FundStatus.Pending)
        assertThat(row.copy(declineStatus = JsonPrimitive("Y"), transferStatus = JsonPrimitive("N"), statusLabel = "Declined").toItem()!!.status).isEqualTo(FundStatus.Declined)
    }

    @Test fun `pending summary is read from meta`() {
        val meta = Json.parseToJsonElement("""{"summary":{"totalPendingRequests":2,"totalPendingAmount":"7500.00"}}""")
        val s = summaryOf(meta)!!
        assertThat(s.pendingCount).isEqualTo(2)
        assertThat(s.pendingAmount).isEqualTo(BigDecimal("7500.00"))
        assertThat(summaryOf(null)).isNull()
    }

    @Test fun `fund requests always go to Admin`() {
        assertThat(adminOf(listOf(RequestFromDto(JsonPrimitive("44"), "Ravi", "distributor"), RequestFromDto(JsonPrimitive("1"), "ASL", "admin")))).isEqualTo("1")
        assertThat(adminOf(emptyList())).isEqualTo("1")
    }

    @Test fun `bank account - acctype 2 is a relative's account, numbers masked`() {
        val a = BankAccountDto(id = 3, bank_name = "UCO BANK", accno = "32840110063873", holdername = "AJAY", acctype = "2", is_verified = true).toAccount()!!
        assertThat(a.owner).isEqualTo(AccountOwner.Relative)
        assertThat(a.accountLast4).isEqualTo("3873")
        assertThat(a.verified).isTrue()
        assertThat(BankAccountDto(id = 4, accountType = "1").toAccount()!!.owner).isEqualTo(AccountOwner.Self)
        assertThat(BankAccountDto(id = 5).toAccount()!!.verified).isFalse()
    }

    @Test fun `ledger text hides long account numbers`() {
        val e = LedgerDto(id = 1, transdesc = "CHARGES FOR VERIFY (A/C : 0185104000179539)", debitAmt = JsonPrimitive("3.00")).toEntry()!!
        assertThat(e.title).isEqualTo("CHARGES FOR VERIFY (A/C : ••••9539)")
        assertThat(e.amount).isEqualTo(BigDecimal("-3.00"))
    }
}

// ---------------------------------------------------------------- repository against a fake server

class WalletRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: WalletRepositoryImpl
    private val account = SendAccount("15", "UCO BANK", "3873", "AJAY KUMAR", "UCBA0003284")

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = WalletRepositoryImpl(retrofit.create(WalletApi::class.java), json)
    }
    @After fun tearDown() = server.shutdown()
    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    @Test fun `account info reads snake and camel case, a missing balance is an error not zero`() = runTest {
        respond(200, """{"status":true,"data":{"available_balance_amt":"1022.00","e_topup_purchase":"5000","sales_amt":"0","commission_received":"12.50"}}""")
        val o = (repo.overview() as NetworkResult.Success).data
        assertThat(o.balance).isEqualTo(BigDecimal("1022.00"))
        assertThat(o.commission).isEqualTo(BigDecimal("12.50"))
        respond(200, """{"status":true,"data":{"sales_amt":"0"}}""")
        assertThat(repo.overview()).isInstanceOf(NetworkResult.Failure::class.java)
    }

    @Test fun `add money info uses the server limits (UAT 500 to 2000)`() = runTest {
        respond(200, """{"status":true,"data":{"wallet":{"balanceAmt":"1022.00"},"limits":{"minAmount":500,"maxAmount":2000},"paymentModes":["PAYU","UPI"]}}""")
        val i = (repo.addMoneyInfo() as NetworkResult.Success).data
        assertThat(i.limits).isEqualTo(Limits(BigDecimal(500), BigDecimal(2000)))
        assertThat(i.balance).isEqualTo(BigDecimal("1022.00"))
    }

    @Test fun `creating an order sends only the amount, with the Idempotency-Key header`() = runTest {
        respond(200, """{"status":true,"data":{"clientTxnId":"UPI1","paymentUrl":"https://pay","status":"INITIATED"}}""")
        val key = IdempotencyKey.new()
        assertThat((repo.createOrder(BigDecimal(1000), key) as NetworkResult.Success).data).isEqualTo(OrderResult.NotSupportedYet)
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo(key.value)
        assertThat(req.body.readUtf8()).isEqualTo("""{"amount":"1000"}""")
    }

    @Test fun `send money - OTP step has no key, the transfer step has one`() = runTest {
        respond(200, """{"status":true,"data":{"step":"OTP_SENT"}}""")
        respond(200, """{"status":true,"data":{"step":"COMPLETED","servid":"PAY63","refno":"6308","payamt":"2000.00","status":"SUCCESS","bankName":"UCO BANK","accountNumber":"32840110063873"}}""")
        assertThat(repo.sendOtp(account, BigDecimal(2000), "IMPS")).isInstanceOf(NetworkResult.Success::class.java)
        val key = IdempotencyKey.new()
        val out = repo.sendMoney(account, BigDecimal(2000), "IMPS", "1234", key) as SendOutcome.Sent
        assertThat(out.receipt.accountLast4).isEqualTo("3873")
        assertThat(out.receipt.txnId).isEqualTo("PAY63")
        val otp = server.takeRequest()
        assertThat(otp.getHeader("Idempotency-Key")).isNull()
        assertThat(otp.body.readUtf8()).isEqualTo("""{"payto":"15","payamt":"2000","paytype":"IMPS"}""")
        val send = server.takeRequest()
        assertThat(send.getHeader("Idempotency-Key")).isEqualTo(key.value)
        assertThat(send.body.readUtf8()).contains("\"otp\":\"1234\"")
    }

    @Test fun `send money - server PENDING, a 5xx or a timeout is Pending, a 4xx is Failed`() = runTest {
        respond(200, """{"status":true,"data":{"step":"COMPLETED","status":"PENDING"}}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new())).isInstanceOf(SendOutcome.Pending::class.java)
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new())).isEqualTo(SendOutcome.Pending(null))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new())).isEqualTo(SendOutcome.Pending(null))
        respond(400, """{"status":false,"message":"Low wallet balance for payout transfer"}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new())).isEqualTo(SendOutcome.Failed("Low wallet balance for payout transfer"))
    }

    @Test fun `send setup lists verified accounts only`() = runTest {
        respond(200, """{"status":true,"data":{"accounts":[{"id":"15","bankName":"UCO BANK","accno":"32840110063873","isverify":"1"},{"id":"16","bankName":"AXIS","accno":"9","isverify":"0"}],
            "wallet":{"balanceAmt":"2022.00"},"limits":{"minAmount":10,"maxAmount":50000},"payoutModes":["IMPS"],"notes":[" note "],"user":{"phone":"9798143077"}}}""")
        val s = (repo.sendSetup() as NetworkResult.Success).data
        assertThat(s.accounts.map { it.id }).containsExactly("15")
        assertThat(s.notes).containsExactly("note")
        assertThat(s.otpTo).isEqualTo("97XXX XX077")
    }

    @Test fun `add bank account uploads both photos as files with the server's field names`() = runTest {
        respond(201, """{"status":true,"data":{"id":9,"bank_name":"UCO BANK","accno":"32840110063873","holdername":"AJAY KUMAR","acctype":"1","is_verified":false}}""")
        val form = AddBankForm(BankOption("12", "UCO BANK"), "32840110063873", "32840110063873", "ucba0003284", "ajay kumar", AccountOwner.Self, 3, 3)
        val a = (repo.addBank(form, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)) as NetworkResult.Success).data
        assertThat(a.verified).isFalse()
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("name=\"panimg\"; filename=\"panimg.jpg\"")
        assertThat(body).contains("name=\"passbookimg\"; filename=\"passbookimg.jpg\"")
        assertThat(body).contains("UCBA0003284")
        assertThat(body).contains("AJAY KUMAR")
        assertThat(body).contains("name=\"acctype\"")
    }

    @Test fun `fund requests read rows and meta summary, filter maps to the server's words`() = runTest {
        respond(200, """{"status":true,"data":[{"id":1,"requestAmount":"5000","status":"Pending","tranId":"UTR1"}],
            "meta":{"summary":{"totalPendingRequests":1,"totalPendingAmount":5000}},"pagination":{"currentPage":1,"totalPages":1}}""")
        val p = (repo.fundRequests(1, FundStatus.Approved) as NetworkResult.Success).data
        assertThat(p.items).hasSize(1)
        assertThat(p.summary!!.pendingCount).isEqualTo(1)
        assertThat(p.canLoadMore).isFalse()
        assertThat(server.takeRequest().path).isEqualTo("/merchant/fund-requests?page=1&limit=20&status=transferred&sort=desc")
    }

    @Test fun `empty report falls back to the retailer's recent requests from fund-request (UAT 5 Nov)`() = runTest {
        respond(200, """{"status":true,"data":[],"meta":{"summary":{"totalPendingRequests":0,"totalPendingAmount":0}},"pagination":{"currentPage":1,"totalPages":1,"hasNext":false}}""")
        respond(200, """{"status":true,"data":{"requestFromOptions":[{"id":"1","name":"Admin","role":"Admin"}],"banks":[],"paymentModes":["CASH"],
            "recentRequests":[{"id":130,"tranId":"APK-INJI","amount":1000,"transferStatus":"Y","declineStatus":"N","statusLabel":"Approved"},
                              {"id":131,"tranId":"UTR2","amount":500,"transferStatus":"N","declineStatus":"N","statusLabel":"Pending"}]}}""")
        val p = (repo.fundRequests(1, null) as NetworkResult.Success).data
        assertThat(p.items.map { it.id }).containsExactly(130L, 131L).inOrder()
        assertThat(p.summary).isEqualTo(com.asl.wallet.feature.wallet.domain.FundSummary(1, BigDecimal(500)))
        assertThat(p.canLoadMore).isFalse()
    }

    @Test fun `fund request setup error (UAT 500 today) is a failure, and the body is the server's format`() = runTest {
        respond(500, """{"status":false,"message":"Invalid `prisma.acc_bank_db.findMany()` invocation: column does not exist"}""")
        assertThat(repo.fundSetup()).isInstanceOf(NetworkResult.Failure::class.java)
        respond(200, """{"status":true,"data":{"id":42}}""")
        val form = FundRequestForm(CompanyBank(3, "UCO", "1234", null), "5000", FundMode.Imps, "utr123", Day(2026, 11, 4), " ")
        assertThat((repo.submitFund(form, "1") as NetworkResult.Success).data).isEqualTo("FR-42")
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"amount":"5000","reqform":"1","bankid":3,"tranid":"UTR123","pwerby":"IMPS","dipodate":"2026-11-04"}""")
    }
}

// ---------------------------------------------------------------- ViewModels

private open class FakeRepo : WalletRepository {
    var overview: NetworkResult<WalletOverview> = NetworkResult.Success(WalletOverview(BigDecimal(1022), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), null)
    var info: NetworkResult<AddMoneyInfo> = NetworkResult.Success(AddMoneyInfo(BigDecimal(1022), Limits(BigDecimal(500), BigDecimal(2000)), listOf("PAYU")), null)
    var order: NetworkResult<OrderResult> = NetworkResult.Success(OrderResult.Ready(PayUOrder("T1", BigDecimal(1000), mapOf("txnid" to "T1"))), null)
    var statuses = ArrayDeque<AddMoneyResult>()
    var orderKeys = mutableListOf<IdempotencyKey>()
    var fund: NetworkResult<FundRequestSetup> = NetworkResult.Failure.Api(500, null, "column does not exist")
    var send: NetworkResult<SendMoneySetup> = NetworkResult.Success(
        SendMoneySetup(listOf(SendAccount("15", "UCO", "3873", "AJAY", "UCBA0003284")), BigDecimal(2022), Limits(BigDecimal(10), BigDecimal(50000)), listOf("IMPS"), emptyList(), null), null,
    )
    var sendOut = ArrayDeque<SendOutcome>()
    var sendKeys = mutableListOf<IdempotencyKey>()

    override suspend fun overview() = overview
    override suspend fun recent(): NetworkResult<List<WalletEntry>> = NetworkResult.Success(emptyList(), null)
    override suspend fun addMoneyInfo() = info
    override suspend fun createOrder(amount: BigDecimal, key: IdempotencyKey) = order.also { orderKeys += key }
    override suspend fun addMoneyStatus(txnId: String, amount: BigDecimal?): NetworkResult<AddMoneyResult> =
        NetworkResult.Success(statuses.removeFirstOrNull() ?: AddMoneyResult.Pending(txnId, amount), null)
    override suspend fun payuHash(txnId: String, hashName: String, hashString: String): String? = null
    override suspend fun fundSetup() = fund
    override suspend fun submitFund(form: FundRequestForm, adminId: String, editId: Long?): NetworkResult<String?> = NetworkResult.Success("FR-1", null)
    override suspend fun deleteFund(id: Long): NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    override suspend fun fundRequests(page: Int, status: FundStatus?): NetworkResult<FundRequestPage> = NetworkResult.Success(FundRequestPage(emptyList(), null, false), null)
    override suspend fun sendSetup() = send
    override suspend fun sendOtp(account: SendAccount, amount: BigDecimal, mode: String): NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    override suspend fun sendMoney(account: SendAccount, amount: BigDecimal, mode: String, otp: String, key: IdempotencyKey): SendOutcome {
        sendKeys += key; return sendOut.removeFirst()
    }
    override suspend fun bankAccounts(): NetworkResult<List<BankAccount>> = NetworkResult.Success(emptyList(), null)
    override suspend fun bankOptions(): NetworkResult<List<BankOption>> = NetworkResult.Success(emptyList(), null)
    override suspend fun addBank(form: AddBankForm, pan: ByteArray, passbook: ByteArray): NetworkResult<BankAccount> = NetworkResult.Failure.NoInternet
    override suspend fun deleteBank(id: Long): NetworkResult<Unit> = NetworkResult.Success(Unit, null)
}

private object NoPayU : PayUCheckout {
    override fun open(activity: android.app.Activity, order: PayUOrder, production: Boolean, hashes: com.asl.wallet.feature.wallet.payu.HashSource, onReturn: (PayUReturn) -> Unit) = Unit
}

class WalletViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun `wallet never shows a zero balance when account-info fails`() = runTest {
        val repo = FakeRepo().apply { overview = NetworkResult.Failure.NoInternet }
        val vm = WalletViewModel(repo).also { it.load() }
        assertThat(vm.state.value.overview).isNull()
        assertThat(vm.state.value.error).isNotNull()
    }

    @Test fun `PayU off - Pay shows opens soon and never creates an order`() = runTest {
        val repo = FakeRepo()
        val vm = AddMoneyViewModel(repo, WalletConfig(payuEnabled = false, payuProduction = false), SavedStateHandle(), NoPayU)
        vm.onAmount("1000"); vm.pay()
        assertThat(vm.state.value.showSoon).isTrue()
        assertThat(repo.orderKeys).isEmpty()
    }

    @Test fun `amount outside the server limits is stopped in the app`() = runTest {
        val repo = FakeRepo()
        val vm = AddMoneyViewModel(repo, WalletConfig(true, false), SavedStateHandle(), NoPayU)
        vm.onAmount("5000"); vm.pay()
        assertThat(vm.state.value.showErrors).isTrue()
        assertThat(vm.state.value.problem).isEqualTo(AmountProblem.AboveMax)
        assertThat(repo.orderKeys).isEmpty()
    }

    @Test fun `a failed order keeps its key for the retry, a new amount gets a new key`() = runTest {
        val repo = FakeRepo().apply { order = NetworkResult.Failure.Timeout }
        val vm = AddMoneyViewModel(repo, WalletConfig(true, false), SavedStateHandle(), NoPayU)
        vm.onAmount("1000"); vm.pay(); advanceUntilIdle(); vm.pay(); advanceUntilIdle()
        vm.onAmount("1500"); vm.pay(); advanceUntilIdle()
        assertThat(repo.orderKeys).hasSize(3)
        assertThat(repo.orderKeys[0]).isEqualTo(repo.orderKeys[1])
        assertThat(repo.orderKeys[2]).isNotEqualTo(repo.orderKeys[0])
        assertThat(vm.state.value.phase).isEqualTo(AddPhase.Form)
    }

    @Test fun `after PayU the server decides - success shows the new balance, cancel with no payment is failed`() = runTest {
        val repo = FakeRepo().apply { statuses += AddMoneyResult.Success("T1", BigDecimal(1000), "UPI", "4039") }
        val vm = AddMoneyViewModel(repo, WalletConfig(true, false), SavedStateHandle(), NoPayU)
        val order = PayUOrder("T1", BigDecimal(1000), emptyMap())
        vm.onPayUReturn(order, PayUReturn.Finished)
        advanceUntilIdle()
        assertThat(vm.state.value.result).isInstanceOf(AddMoneyResult.Success::class.java)
        assertThat(vm.state.value.newBalance).isEqualTo(BigDecimal(1022))

        val vm2 = AddMoneyViewModel(FakeRepo(), WalletConfig(true, false), SavedStateHandle(), NoPayU)
        vm2.onPayUReturn(order, PayUReturn.Cancelled)
        advanceUntilIdle()
        assertThat(vm2.state.value.result).isInstanceOf(AddMoneyResult.Failed::class.java)

        val vm3 = AddMoneyViewModel(FakeRepo(), WalletConfig(true, false), SavedStateHandle(), NoPayU)
        vm3.onPayUReturn(order, PayUReturn.Finished)
        advanceUntilIdle()
        assertThat(vm3.state.value.result).isInstanceOf(AddMoneyResult.Pending::class.java) // never "success" without the server
    }

    @Test fun `fund request 500 shows Unavailable, not an error code`() = runTest {
        val vm = FundRequestViewModel(FakeRepo(), WalletSession())
        assertThat(vm.state.value.phase).isEqualTo(FundPhase.Unavailable)
    }

    @Test fun `editing a pending request prefills the form from the hand-over`() = runTest {
        val bank = CompanyBank(3, "UCO", "32840110063873", null)
        val repo = FakeRepo().apply { fund = NetworkResult.Success(FundRequestSetup("1", listOf(bank), FundMode.entries), null) }
        val item = FundRequestItem(9, "FR-9", BigDecimal("5000.00"), FundMode.Upi, 3, "UCO", "3873", "UTR99", "2026-11-03", "shop", null, "2026-11-03", "", FundStatus.Pending)
        val session = WalletSession().apply { editing = item }
        val vm = FundRequestViewModel(repo, session)
        val f = vm.state.value.form
        assertThat(f.bank).isEqualTo(bank)
        assertThat(f.amountText).isEqualTo("5000")
        assertThat(f.date).isEqualTo(Day(2026, 11, 3))
        assertThat(session.editing).isNull() // read once
    }

    @Test fun `send - wrong OTP stays on the sheet with a fresh key, success keeps one key, no account leads to Add`() = runTest {
        val repo = FakeRepo().apply {
            sendOut += SendOutcome.Failed("Invalid OTP provided for payout verification")
            sendOut += SendOutcome.Pending(null)
        }
        val vm = SendMoneyViewModel(repo)
        vm.onAmount("2000"); vm.proceed()
        assertThat(vm.state.value.otpOpen).isTrue()
        vm.onOtp("1111"); vm.confirm()
        assertThat(vm.state.value.otpOpen).isTrue()
        assertThat(vm.state.value.otpError).contains("OTP")
        vm.onOtp("1234"); vm.confirm()
        assertThat(vm.state.value.phase).isEqualTo(SendPhase.Sending) // held at least ~2 s, so it's seen
        advanceUntilIdle()
        assertThat(vm.state.value.phase).isEqualTo(SendPhase.Result)
        assertThat(vm.state.value.outcome).isInstanceOf(SendOutcome.Pending::class.java)
        assertThat(repo.sendKeys[0]).isNotEqualTo(repo.sendKeys[1])
        vm.confirm() // already sent: nothing more goes out
        assertThat(repo.sendKeys).hasSize(2)

        val none = FakeRepo().apply { send = NetworkResult.Success((send as NetworkResult.Success).data.copy(accounts = emptyList()), null) }
        assertThat(SendMoneyViewModel(none).state.value.phase).isEqualTo(SendPhase.NoAccount)
    }

    @Test fun `send - over the wallet balance never asks for an OTP`() = runTest {
        val vm = SendMoneyViewModel(FakeRepo())
        vm.onAmount("3000"); vm.proceed()
        assertThat(vm.state.value.otpOpen).isFalse()
        assertThat(vm.state.value.problem).isEqualTo(AmountProblem.OverBalance)
    }
}
