package com.asl.wallet.feature.wallet

import androidx.lifecycle.SavedStateHandle
import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.core.testing.MainDispatcherRule
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
    private val account = SendAccount("15", "UCO BANK", "3873", "AJAY KUMAR", "UCBA0003284", accountNumber = "32840110063873")
    private val shop = com.asl.wallet.feature.wallet.data.ShopLocation(22.5726, 88.3639)

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = WalletRepositoryImpl(retrofit.create(WalletApi::class.java), json, javax.inject.Provider<com.asl.wallet.core.common.session.CurrentUser> { TestUser })
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

    @Test fun `PayU hash ask carries postSalt and hashType, and a refusal gives no hash`() = runTest {
        respond(200, """{"status":true,"data":{"hash":"abc"}}""")
        val ask = com.asl.wallet.feature.wallet.payu.HashAsk("T1", "payment_source", "K|T1|1500.00|", postSalt = "PS", hashType = "V2")
        assertThat(repo.payuHash(ask)).isEqualTo("abc")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains(""""postSalt":"PS"""")
        assertThat(body).contains(""""hashType":"V2"""")
        respond(403, """{"status":false,"message":"Hash request not allowed"}""")
        assertThat(repo.payuHash(ask.copy(postSalt = null, hashType = null))).isNull()
    }

    @Test fun `PayU initiate - data payu fields open the SDK, status uses the payments payu route`() = runTest {
        respond(201, """{"status":true,"data":{"txnid":"ASLW1","clientTxnId":"ASLW1","amount":"1500.00","payu":{"key":"K","txnid":"ASLW1","amount":"1500.00",
            "productinfo":"ASL Wallet Topup","firstname":"A","email":"a@b.in","surl":"https://h/s","furl":"https://h/f","udf1":"","hash":"H"}}}""")
        val ready = (repo.createOrder(BigDecimal("1500.00"), IdempotencyKey.new()) as NetworkResult.Success).data as OrderResult.Ready
        assertThat(ready.order.txnId).isEqualTo("ASLW1")
        assertThat(ready.order.statusId).isEqualTo("ASLW1") // this sample has clientTxnId = txnid
        assertThat(ready.order.params["udf1"]).isEqualTo("")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"amount":1500,"paymentPurpose":"ASL Wallet Topup","isAndroid":true}""")

        // Live shape (9 Nov 2026): fields straight under data, with a separate clientTxnId for the status API.
        respond(200, """{"status":true,"data":{"clientTxnId":"PAYU6044_1","txnid":"ASL6044T1","amount":"1.00","key":"K","productinfo":"P",
            "firstname":"A","email":"a@b.in","surl":"https://h/s","furl":"https://h/f","hash":"H"}}""")
        val live = (repo.createOrder(BigDecimal("1"), IdempotencyKey.new()) as NetworkResult.Success).data as OrderResult.Ready
        assertThat(live.order.txnId).isEqualTo("ASL6044T1")
        assertThat(live.order.statusId).isEqualTo("PAYU6044_1")
        server.takeRequest()

        respond(200, """{"status":true,"data":{"txnid":"ASLW1","amount":"1500.00","status":"SUCCESS","isPaymentComplete":true,"walletCredited":false}}""")
        assertThat((repo.addMoneyStatus("ASLW1", null) as NetworkResult.Success).data).isInstanceOf(AddMoneyResult.Pending::class.java)
        assertThat(server.takeRequest().path).isEqualTo("/api/v1/payments/payu/ASLW1/status")
    }

    @Test fun `creating an order sends only the amount, with the Idempotency-Key header`() = runTest {
        respond(200, """{"status":true,"data":{"clientTxnId":"UPI1","paymentUrl":"https://pay","status":"INITIATED"}}""")
        val key = IdempotencyKey.new()
        assertThat((repo.createOrder(BigDecimal(1000), key) as NetworkResult.Success).data).isEqualTo(OrderResult.NotSupportedYet)
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo(key.value)
        assertThat(req.path).isEqualTo("/api/v1/payments/payu/initiate")
        assertThat(req.body.readUtf8()).isEqualTo("""{"amount":1000,"paymentPurpose":"ASL Wallet Topup","isAndroid":true}""")
    }

    @Test fun `payout - OTP from payout-send-otp, then payout with the full account, location and one key`() = runTest {
        respond(200, """{"status":true,"message":"Verification OTP sent successfully","data":{"fname":"Ajay","mob":"9798143077"}}""")
        respond(200, """{"status":true,"data":{"tranStatus":"SUCCESS","servid":"PAY63","utr":"6308","amount":"2000.00"}}""")
        assertThat((repo.sendOtp(account, BigDecimal(2000), "IMPS") as NetworkResult.Success).data).isEqualTo("97XXX XX077")
        val key = IdempotencyKey.new()
        val out = repo.sendMoney(account, BigDecimal(2000), "IMPS", "1234", key, shop) as SendOutcome.Sent
        assertThat(out.receipt.accountLast4).isEqualTo("3873")
        assertThat(out.receipt.txnId).isEqualTo("PAY63")
        assertThat(out.receipt.bankRef).isEqualTo("6308")
        val otp = server.takeRequest()
        assertThat(otp.path).isEqualTo("/merchant/payout/payout-send-otp")
        assertThat(otp.getHeader("Idempotency-Key")).isNull()
        val send = server.takeRequest()
        assertThat(send.path).isEqualTo("/merchant/payout/payout")
        assertThat(send.getHeader("Idempotency-Key")).isEqualTo(key.value)
        assertThat(send.body.readUtf8()).isEqualTo(
            """{"accountNumber":"32840110063873","amount":2000,"paymentMode":"IMPS","latitude":"22.572600","longitude":"88.363900","otp":"1234","source":"APPS"}""",
        )
    }

    @Test fun `payout - unclear, 5xx or timeout is Pending, a 4xx or inner refusal is Failed`() = runTest {
        respond(200, """{"status":true,"data":{"tranStatus":"PENDING"}}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isInstanceOf(SendOutcome.Pending::class.java)
        respond(200, """{"status":true,"data":{"servid":"PAY1"}}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isInstanceOf(SendOutcome.Pending::class.java)
        respond(502, """{"status":false,"message":"Bad gateway"}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isEqualTo(SendOutcome.Pending(null))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isEqualTo(SendOutcome.Pending(null))
        respond(400, """{"status":false,"message":"Low wallet balance for payout transfer"}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isEqualTo(SendOutcome.Failed("Low wallet balance for payout transfer"))
        respond(200, """{"status":true,"data":{"status":false,"message":"Invalid OTP"}}""")
        assertThat(repo.sendMoney(account, BigDecimal(10), "IMPS", "1234", IdempotencyKey.new(), shop)).isEqualTo(SendOutcome.Failed("Invalid OTP"))
    }

    @Test fun `payout banks - the UAT shape, every active bank listed with its verified flag, full number kept`() = runTest {
        val list = """{"status":true,"data":[
            {"id":"4040","accno":"32840110063873","ifsccode":"UCBA0003284","holdername":"AJAY KUMAR","acctype":"1","isverify":"ONE","status":"ONE","bankName":"UCO BANK"},
            {"id":"4041","accno":"9123456780","ifsccode":"HDFC0000001","holdername":"AJAY KUMAR","acctype":"2","isverify":"ZERO","status":"ONE","bankName":"HDFC BANK"},
            {"id":"4042","accno":"1111222233","isverify":"ONE","status":"ZERO","bankName":"OLD BANK"}]}"""
        respond(200, list)
        respond(200, """{"status":true,"data":{"available_balance_amt":"2022.00"}}""")
        val s = (repo.sendSetup() as NetworkResult.Success).data
        // Both active banks are listed (the inactive one isn't); only the verified one can be chosen.
        assertThat(s.accounts.map { it.id }).containsExactly("4040", "4041").inOrder()
        assertThat(s.accounts.map { it.verified }).containsExactly(true, false).inOrder()
        assertThat(s.accounts.first().accountNumber).isEqualTo("32840110063873")
        assertThat(s.accounts.first().toString()).doesNotContain("32840110063873")
        assertThat(s.balance).isEqualTo(BigDecimal("2022.00"))
        assertThat(server.takeRequest().path).isEqualTo("/merchant/payout/payout-bank-list")

        respond(200, list)
        val banks = (repo.bankAccounts() as NetworkResult.Success).data
        assertThat(banks.map { it.id }).containsExactly(4040L, 4041L).inOrder()
        assertThat(banks[1].verified).isFalse()
        assertThat(banks[1].owner).isEqualTo(AccountOwner.Relative)
    }

    @Test fun `delete bank names the account by its full number`() = runTest {
        respond(200, """{"status":true,"message":"Deleted"}""")
        val a = BankAccount(4040, "UCO BANK", "3873", "AJAY KUMAR", "UCBA0003284", AccountOwner.Self, true, accountNumber = "32840110063873")
        assertThat(repo.deleteBank(a)).isInstanceOf(NetworkResult.Success::class.java)
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/merchant/payout/delete-bank")
        assertThat(req.body.readUtf8()).isEqualTo("""{"accountNumber":"32840110063873"}""")
        assertThat(repo.deleteBank(a.copy(accountNumber = ""))).isInstanceOf(NetworkResult.Failure::class.java)
    }

    @Test fun `add bank goes to payout add-bank with the collection's field names and the retailer's own reg no`() = runTest {
        respond(200, """{"status":true,"message":"Bank added successfully"}""")
        val form = AddBankForm(BankOption("12", "UCO BANK"), "32840110063873", "32840110063873", "ucba0003284", "ajay kumar", AccountOwner.Self, 3, 3)
        val a = (repo.addBank(form, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)) as NetworkResult.Success).data
        assertThat(a.verified).isFalse()
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/merchant/payout/add-bank")
        val body = req.body.readUtf8()
        assertThat(body).contains("name=\"panImage\"; filename=\"panImage.jpg\"")
        assertThat(body).contains("name=\"passbookImage\"; filename=\"passbookImage.jpg\"")
        for (f in listOf("userRegID", "bankName", "accountNumber", "ifscCode", "accountHolderName", "accountType")) assertThat(body).contains("name=\"$f\"")
        assertThat(body).contains("6044")
        assertThat(body).contains("UCBA0003284")
        assertThat(body).contains("AJAY KUMAR")
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
    override suspend fun payuHash(ask: com.asl.wallet.feature.wallet.payu.HashAsk): String? = null
    override suspend fun fundSetup() = fund
    override suspend fun submitFund(form: FundRequestForm, adminId: String, editId: Long?): NetworkResult<String?> = NetworkResult.Success("FR-1", null)
    override suspend fun deleteFund(id: Long): NetworkResult<Unit> = NetworkResult.Success(Unit, null)
    override suspend fun fundRequests(page: Int, status: FundStatus?): NetworkResult<FundRequestPage> = NetworkResult.Success(FundRequestPage(emptyList(), null, false), null)
    override suspend fun sendSetup() = send
    override suspend fun sendOtp(account: SendAccount, amount: BigDecimal, mode: String): NetworkResult<String?> = NetworkResult.Success("97XXX XX077", null)
    override suspend fun sendMoney(account: SendAccount, amount: BigDecimal, mode: String, otp: String, key: IdempotencyKey, at: com.asl.wallet.feature.wallet.data.ShopLocation): SendOutcome {
        sendKeys += key; return sendOut.removeFirst()
    }
    override suspend fun bankAccounts(): NetworkResult<List<BankAccount>> = NetworkResult.Success(emptyList(), null)
    override suspend fun bankOptions(): NetworkResult<List<BankOption>> = NetworkResult.Success(emptyList(), null)
    override suspend fun addBank(form: AddBankForm, pan: ByteArray, passbook: ByteArray): NetworkResult<BankAccount> = NetworkResult.Failure.NoInternet
    override suspend fun deleteBank(account: BankAccount): NetworkResult<Unit> = NetworkResult.Success(Unit, null)
}

private object TestUser : com.asl.wallet.core.common.session.CurrentUser {
    override val user = kotlinx.coroutines.flow.MutableStateFlow<com.asl.wallet.core.common.session.UserSummary?>(
        com.asl.wallet.core.common.session.UserSummary("Ajay Kumar", "ASL Payzones", 6044, "Retailer", true),
    )
    override suspend fun logout() = Unit
}

private class FakeLocation(
    var permission: Boolean = true,
    var on: Boolean = true,
    var fix: com.asl.wallet.feature.wallet.data.ShopLocation? = com.asl.wallet.feature.wallet.data.ShopLocation(22.57, 88.36),
) : com.asl.wallet.feature.wallet.data.PayoutLocation {
    override fun hasPermission() = permission
    override fun isEnabled() = on
    override suspend fun current() = fix
}

private object NoPayU : PayUCheckout {
    override fun open(activity: android.app.Activity, order: PayUOrder, production: Boolean, hashes: com.asl.wallet.feature.wallet.payu.HashSource, onReturn: (PayUReturn, String?) -> Unit) = Unit
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
        vm.onPayUReturn(order, PayUReturn.Paid, "4039")
        advanceUntilIdle()
        assertThat(vm.state.value.result).isInstanceOf(AddMoneyResult.Success::class.java)
        assertThat(vm.state.value.newBalance).isEqualTo(BigDecimal(1022))

        val vm2 = AddMoneyViewModel(FakeRepo(), WalletConfig(true, false), SavedStateHandle(), NoPayU)
        vm2.onPayUReturn(order, PayUReturn.Cancelled)
        advanceUntilIdle()
        assertThat(vm2.state.value.result).isInstanceOf(AddMoneyResult.Failed::class.java)

        // Cancelled or failed after the payment started: the server still says pending, never success.
        val repo3 = FakeRepo()
        val vm3 = AddMoneyViewModel(repo3, WalletConfig(true, false), SavedStateHandle(), NoPayU)
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
        val vm = SendMoneyViewModel(repo, FakeLocation())
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
        assertThat(SendMoneyViewModel(none, FakeLocation()).state.value.phase).isEqualTo(SendPhase.NoAccount)
    }

    @Test fun `payout - a bank waiting for verification is listed but can't be chosen or sent to`() = runTest {
        val repo = FakeRepo().apply {
            send = NetworkResult.Success((send as NetworkResult.Success).data.copy(accounts = listOf(
                SendAccount("1", "SBI", "9580", "A", "SBIN0008437", "123456789580", verified = true),
                SendAccount("2", "ABH", "7890", "A", "HDFC0CNAG08", "1234567890", verified = false),
            )), null)
        }
        val vm = SendMoneyViewModel(repo, FakeLocation())
        assertThat(vm.state.value.account?.id).isEqualTo("1") // the only verified one is picked
        vm.onAccount((vm.state.value.setup!!.accounts[1]))
        assertThat(vm.state.value.account?.id).isEqualTo("1")
    }

    @Test fun `payout - no location permission or GPS stops before the OTP, no fix sends nothing`() = runTest {
        val repo = FakeRepo().apply { sendOut += SendOutcome.Pending(null) }
        val loc = FakeLocation(permission = false)
        val vm = SendMoneyViewModel(repo, loc)
        vm.onAmount("2000"); vm.proceed()
        assertThat(vm.state.value.location).isEqualTo(com.asl.wallet.feature.wallet.ui.LocationNeed.Permission)
        assertThat(vm.state.value.otpOpen).isFalse()
        loc.permission = true; loc.on = false
        vm.onLocationChanged(true)
        assertThat(vm.state.value.location).isEqualTo(com.asl.wallet.feature.wallet.ui.LocationNeed.TurnOn)
        loc.on = true; loc.fix = null
        vm.proceed()
        assertThat(vm.state.value.otpOpen).isTrue()
        vm.onOtp("1234"); vm.confirm(); advanceUntilIdle()
        assertThat(vm.state.value.location).isEqualTo(com.asl.wallet.feature.wallet.ui.LocationNeed.NoFix)
        assertThat(repo.sendKeys).isEmpty() // nothing was sent
        loc.fix = com.asl.wallet.feature.wallet.data.ShopLocation(22.57, 88.36)
        vm.dismissLocation(); vm.confirm(); advanceUntilIdle()
        assertThat(repo.sendKeys).hasSize(1)
    }

    @Test fun `send - over the wallet balance never asks for an OTP`() = runTest {
        val vm = SendMoneyViewModel(FakeRepo(), FakeLocation())
        vm.onAmount("3000"); vm.proceed()
        assertThat(vm.state.value.otpOpen).isFalse()
        assertThat(vm.state.value.problem).isEqualTo(AmountProblem.OverBalance)
    }
}

class PayUUserCredentialsTest {
    private val launcher = com.asl.wallet.feature.wallet.payu.PayUCheckoutProLauncher(java.util.Optional.empty())
    @org.junit.Test fun `server value wins, else key and regno, else key and mobile, else none`() {
        com.google.common.truth.Truth.assertThat(launcher.userCredentials(mapOf("key" to "K", "user_credentials" to "K:77", "udf1" to "6044"))).isEqualTo("K:77")
        com.google.common.truth.Truth.assertThat(launcher.userCredentials(mapOf("key" to "K", "udf1" to "6044", "phone" to "9798143077"))).isEqualTo("K:6044")
        com.google.common.truth.Truth.assertThat(launcher.userCredentials(mapOf("key" to "K", "udf1" to "", "phone" to "97981 43077"))).isEqualTo("K:9798143077")
        com.google.common.truth.Truth.assertThat(launcher.userCredentials(mapOf("key" to "K"))).isNull()
    }
}
