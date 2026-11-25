package com.asl.wallet.feature.matm

import com.asl.wallet.core.common.session.IdempotencyKey
import com.asl.wallet.core.network.NetworkResult
import com.asl.wallet.feature.matm.data.MatmApi
import com.asl.wallet.feature.matm.data.MatmRepositoryImpl
import com.asl.wallet.feature.matm.domain.MatmResult
import com.asl.wallet.feature.matm.domain.MatmTxnType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class MatmRepositoryTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var repo: MatmRepositoryImpl

    @Before fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        val retrofit = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        repo = MatmRepositoryImpl(retrofit.create(MatmApi::class.java), json)
    }
    @After fun tearDown() = server.shutdown()

    private fun respond(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun result(status: Boolean = true, type: Int = 2) = MatmResult(
        status = status, message = if (status) "Approved" else "Declined", transAmount = 500.0, balanceAmount = 1234.5,
        bankRrn = "526710929638", transType = "CW", type = type, cardNum = "******1042", bankName = "State Bank of India",
        cardType = "VISA", terminalId = "NSD71239", txnId = "7BJ2QMIMWAXX2QH6", mobileNumber = "8709305218",
        fpId = "MABB7370459240925104700732S", errorCode = "00",
    )

    @Test fun `initiate sends amount, mobile and txn_type and maps the SDK credentials`() = runTest {
        respond(200, """{"status":true,"data":{"superMerchantId":"SM1","merchantUserId":"MU1","merchantPassword":"pw",
            "mobileNumber":"8709305218","amount":"500","remarks":"MATM","txnId":"T123","imei":"","amountEditable":false,"type":2}}""")

        val r = repo.initiate(MatmTxnType.Withdraw, "8709305218", BigDecimal(500), IdempotencyKey("matm-test-key-1")) as NetworkResult.Success
        val req = server.takeRequest()
        assertThat(req.getHeader("Idempotency-Key")).isEqualTo("matm-test-key-1")
        val body = req.body.readUtf8()
        assertThat(body).contains("\"txn_type\":\"CW\"")
        assertThat(body).contains("\"customer_mobile\":\"8709305218\"")
        assertThat(body).contains("\"amount\":\"500\"")
        assertThat(r.data.superMerchantId).isEqualTo("SM1")
        assertThat(r.data.merchantUserId).isEqualTo("MU1")
        assertThat(r.data.type).isEqualTo(MatmTxnType.Withdraw)
    }

    @Test fun `initiate reads SDK credentials returned flat at the top level`() = runTest {
        // The real backend returns the credentials next to status (no data wrapper) - this used to
        // surface as a false "Empty response".
        respond(200, """{"status":true,"message":"ok","superMerchantId":"SM1","merchantUserId":"MU1",
            "merchantPassword":"pw","mobileNumber":"8709305218","amount":"500","txnId":"T123","amountEditable":false,"type":2}""")

        val r = repo.initiate(MatmTxnType.Withdraw, "8709305218", BigDecimal(500), IdempotencyKey("matm-flat-key")) as NetworkResult.Success
        assertThat(r.data.superMerchantId).isEqualTo("SM1")
        assertThat(r.data.merchantUserId).isEqualTo("MU1")
        assertThat(r.data.type).isEqualTo(MatmTxnType.Withdraw)
    }

    @Test fun `update-status reads a flat server error as not ok`() = runTest {
        respond(200, """{"status":true,"error":"Pending at bank"}""")
        val r = repo.updateStatus(result()) as NetworkResult.Success
        assertThat(r.data.ok).isFalse()
        assertThat(r.data.message).isEqualTo("Pending at bank")
    }

    @Test fun `balance enquiry sends BE and amount 0`() = runTest {
        respond(200, """{"status":true,"data":{"superMerchantId":"SM1","type":4}}""")
        val r = repo.initiate(MatmTxnType.Balance, "8709305218", BigDecimal.ZERO, IdempotencyKey("matm-test-key-2")) as NetworkResult.Success
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"txn_type\":\"BE\"")
        assertThat(body).contains("\"amount\":\"0\"")
        assertThat(r.data.type).isEqualTo(MatmTxnType.Balance)
    }

    @Test fun `update-status posts the device fields verbatim`() = runTest {
        respond(200, """{"status":true,"data":{"tranStatus":"SUCCESS","tranCommAmt":"3.50"}}""")
        val r = repo.updateStatus(result()) as NetworkResult.Success
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"bankRrn\":\"526710929638\"")
        assertThat(body).contains("\"fpId\":\"MABB7370459240925104700732S\"")
        assertThat(body).contains("\"status\":true")
        assertThat(body).contains("\"type\":\"2\"")
        assertThat(r.data.ok).isTrue()
        assertThat(r.data.commission).isEqualTo(BigDecimal("3.50"))
    }

    @Test fun `update-status marks a FAILED server status as not ok`() = runTest {
        respond(200, """{"status":true,"data":{"tranStatus":"FAILED"}}""")
        val r = repo.updateStatus(result(status = false)) as NetworkResult.Success
        assertThat(r.data.ok).isFalse()
        assertThat(r.data.commission).isNull()
    }
}
