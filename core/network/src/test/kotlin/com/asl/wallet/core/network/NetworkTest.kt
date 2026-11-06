package com.asl.wallet.core.network

import app.cash.turbine.test
import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.common.session.TokenStore
import com.asl.wallet.core.network.di.NetworkModule
import com.asl.wallet.core.network.interceptor.ApiHeaders
import com.asl.wallet.core.network.interceptor.AuthInterceptor
import com.asl.wallet.core.network.interceptor.CommonHeadersInterceptor
import com.asl.wallet.core.network.interceptor.SessionEvents
import com.asl.wallet.core.network.interceptor.TokenAuthenticator
import com.asl.wallet.core.network.model.ApiEnvelope
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers

@Serializable data class Profile(val name: String)

interface TestApi {
    @GET("me") suspend fun me(): Response<ApiEnvelope<Profile>>
    @Headers(ApiHeaders.NO_AUTH_VALUE) @GET("public") suspend fun publicCall(): Response<ApiEnvelope<Profile>>
}

class FakeTokenStore(initial: SessionTokens?) : TokenStore {
    val state = MutableStateFlow(initial)
    var cleared = false
    override suspend fun read() = state.value
    override suspend fun save(tokens: SessionTokens) { state.value = tokens }
    override suspend fun clear() { cleared = true; state.value = null }
    override val isSignedIn: Flow<Boolean> = state.map { it != null }
}

class NetworkTest {
    private lateinit var server: MockWebServer
    private val json = NetworkModule.json()
    private val config = NetworkConfig("https://example.test/", emptyMap(), isDebug = true, appVersion = "1.0.0", flavor = "uat")

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun api(tokens: FakeTokenStore, events: SessionEvents = SessionEvents()): TestApi {
        val base = OkHttpClient.Builder().build()
        val client = base.newBuilder()
            .addInterceptor(CommonHeadersInterceptor(config))
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, events, json, base, server.url("/merchant/auth/refresh-token")))
            .build()
        return Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(TestApi::class.java)
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    @Test fun `success envelope maps to Success and ignores unknown fields`() = runTest {
        server.enqueue(ok("""{"status":true,"statusCode":200,"message":"ok","data":{"name":"Rahim","userpass":"x"}}"""))
        val r = safeApiCall(json) { api(FakeTokenStore(null)).me() }
        assertThat(r).isEqualTo(NetworkResult.Success(Profile("Rahim"), "ok"))
    }

    @Test fun `status false with 200 maps to Api failure with server message`() = runTest {
        server.enqueue(ok("""{"status":false,"statusCode":422,"message":"Amount too low"}"""))
        val r = safeApiCall(json) { api(FakeTokenStore(null)).me() }
        assertThat(r).isEqualTo(NetworkResult.Failure.Api(422, null, "Amount too low"))
    }

    @Test fun `4xx error body message is surfaced`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(
            """{"status":false,"statusCode":404,"message":"x","error":{"code":"NOT_FOUND","message":"Bank account not found"}}""",
        ))
        val r = safeApiCall(json) { api(FakeTokenStore(null)).me() }
        assertThat(r).isEqualTo(NetworkResult.Failure.Api(404, "NOT_FOUND", "Bank account not found"))
    }

    @Test fun `429 with a non-boolean status still shows the server's message (UAT 5 Nov)`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody(
            """{"status":"fail","statusCode":429,"message":"Too many sensitive requests (OTP/Password Reset), please try again after 15 minutes","data":null}""",
        ))
        val r = safeApiCall(json) { api(FakeTokenStore(null)).me() } as NetworkResult.Failure.Api
        assertThat(r.httpCode).isEqualTo(429)
        assertThat(r.message).startsWith("Too many sensitive requests")
    }

    @Test fun `server down maps to NoInternet`() = runTest {
        val a = api(FakeTokenStore(null))
        server.shutdown()
        assertThat(safeApiCall(json) { a.me() }).isEqualTo(NetworkResult.Failure.NoInternet)
    }

    @Test fun `adds bearer token, request id and platform headers`() = runTest {
        server.enqueue(ok("""{"status":true,"data":{"name":"a"}}"""))
        server.enqueue(ok("""{"status":true,"data":{"name":"a"}}"""))
        val a = api(FakeTokenStore(SessionTokens("acc-1", "ref-1")))
        a.me(); a.me()
        val r1 = server.takeRequest(); val r2 = server.takeRequest()
        assertThat(r1.getHeader("Authorization")).isEqualTo("Bearer acc-1")
        assertThat(r1.getHeader(ApiHeaders.PLATFORM)).isEqualTo("android")
        assertThat(r1.getHeader(ApiHeaders.REQUEST_ID)).isNotEmpty()
        assertThat(r1.getHeader(ApiHeaders.REQUEST_ID)).isNotEqualTo(r2.getHeader(ApiHeaders.REQUEST_ID))
    }

    @Test fun `no-auth endpoints never send the token or the marker header`() = runTest {
        server.enqueue(ok("""{"status":true,"data":{"name":"a"}}"""))
        api(FakeTokenStore(SessionTokens("acc-1", "ref-1"))).publicCall()
        val r = server.takeRequest()
        assertThat(r.getHeader("Authorization")).isNull()
        assertThat(r.getHeader(ApiHeaders.NO_AUTH)).isNull()
    }

    @Test fun `401 refreshes once, saves new tokens and retries`() = runTest {
        val tokens = FakeTokenStore(SessionTokens("old", "ref-1"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(ok("""{"status":true,"data":{"accessToken":"new","refreshToken":"ref-2"}}"""))
        server.enqueue(ok("""{"status":true,"data":{"name":"Rahim"}}"""))

        val r = safeApiCall(json) { api(tokens).me() }

        assertThat(r).isInstanceOf(NetworkResult.Success::class.java)
        assertThat(tokens.state.value).isEqualTo(SessionTokens("new", "ref-2"))
        server.takeRequest()
        val refresh = server.takeRequest()
        assertThat(refresh.path).isEqualTo("/merchant/auth/refresh-token")
        assertThat(refresh.body.readUtf8()).contains("ref-1")
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer new")
    }

    @Test fun `failed refresh clears tokens and emits SessionExpired`() = runTest {
        val tokens = FakeTokenStore(SessionTokens("old", "ref-1"))
        val events = SessionEvents()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(401)) // refresh rejected
        events.signOut.test {
            val r = safeApiCall(json) { api(tokens, events).me() }
            assertThat(r).isEqualTo(NetworkResult.Failure.Unauthorized)
            assertThat(awaitItem()).isEqualTo(SignOutReason.SessionExpired)
        }
        assertThat(tokens.cleared).isTrue()
    }

    @Test fun `on sign-in endpoints a 401 keeps the server message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(
            """{"status":false,"statusCode":401,"message":"Invalid credentials. 4 attempt(s) remaining before account lockout."}""",
        ))
        val r = safeApiCall(json, authenticated = false) { api(FakeTokenStore(null)).publicCall() }
        assertThat(r).isEqualTo(NetworkResult.Failure.Api(401, null, "Invalid credentials. 4 attempt(s) remaining before account lockout."))
    }

    @Test fun `pagination shapes are normalised`() {
        val a = json.decodeFromString(ApiEnvelope.serializer(Profile.serializer()),
            """{"status":true,"data":{"name":"a"},"pagination":{"page":1,"limit":5,"total":7,"totalPages":2}}""").pagination!!
        val b = json.decodeFromString(ApiEnvelope.serializer(Profile.serializer()),
            """{"status":true,"data":{"name":"a"},"pagination":{"currentPage":1,"limit":20,"totalRecords":5,"totalPages":1,"hasNext":false}}""").pagination!!
        assertThat(a.canLoadMore).isTrue()
        assertThat(a.totalOrNull).isEqualTo(7)
        assertThat(b.canLoadMore).isFalse()
        assertThat(b.pageOrNull).isEqualTo(1)
    }

    @Test fun `release config rejects missing pins and http`() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkConfig("https://x.test/", emptyMap(), isDebug = false, appVersion = "1", flavor = "live")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkConfig("http://x.test/", emptyMap(), isDebug = true, appVersion = "1", flavor = "uat")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkConfig("https://x.test/", mapOf("x.test" to listOf("sha256/a=")), isDebug = false, appVersion = "1", flavor = "live")
        }
    }

    @Test fun `base client pins every configured host and never auto-retries`() {
        val c = NetworkModule.buildBaseClient(
            NetworkConfig("https://x.test/", mapOf("x.test" to listOf("sha256/AAAA", "sha256/BBBB")), isDebug = false, appVersion = "1", flavor = "uat"),
        )
        assertThat(c.certificatePinner.pins.map { it.pattern }.toSet()).containsExactly("x.test")
        assertThat(c.certificatePinner.pins).hasSize(2)
        assertThat(c.retryOnConnectionFailure).isFalse()
        assertThat(c.followRedirects).isFalse()
    }
}

class StaleConnectionRetryTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(com.asl.wallet.core.network.interceptor.StaleConnectionRetryInterceptor())
        .build()

    @Before fun setUp() = server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
    @After fun tearDown() = server.shutdown()

    /** IPv4 literal: "localhost" also resolves to ::1, where MockWebServer is not listening. */
    private fun url(path: String) = server.url(path).newBuilder().host("127.0.0.1").build()

    private fun dropped() = MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST)

    @Test fun `GET on a dropped connection is retried once and succeeds`() {
        server.enqueue(dropped())
        server.enqueue(MockResponse().setBody("ok"))
        val r = client.newCall(okhttp3.Request.Builder().url(url("/merchant/ledger-report")).build()).execute()
        assertThat(r.body!!.string()).isEqualTo("ok")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `GET is retried only once`() {
        server.enqueue(dropped()); server.enqueue(dropped()); server.enqueue(MockResponse().setBody("never"))
        assertThrows(java.io.IOException::class.java) {
            client.newCall(okhttp3.Request.Builder().url(url("/x")).build()).execute()
        }
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `POST is never retried, so money is never sent twice`() {
        server.enqueue(dropped()); server.enqueue(MockResponse().setBody("second"))
        val body = okhttp3.RequestBody.Companion.run { "{}".toRequestBody("application/json".toMediaType()) }
        assertThrows(java.io.IOException::class.java) {
            client.newCall(okhttp3.Request.Builder().url(url("/merchant/payout")).post(body).build()).execute()
        }
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `timeouts and TLS errors are not treated as stale connections`() {
        val f = com.asl.wallet.core.network.interceptor.StaleConnectionRetryInterceptor.Companion::isStaleConnection
        assertThat(f(java.net.SocketTimeoutException("timeout"))).isFalse()
        assertThat(f(javax.net.ssl.SSLPeerUnverifiedException("pin"))).isFalse()
        assertThat(f(java.net.UnknownHostException("dns"))).isFalse()
        assertThat(f(java.io.IOException("unexpected end of stream on https://x/..."))).isTrue()
        assertThat(f(java.io.EOFException())).isTrue()
    }
}

class UiErrorTest {
    @Test fun `server text is shown only when it is safe`() {
        assertThat(NetworkResult.Failure.Api(400, null, "Incorrect current password.").toUiError()).isEqualTo(UiError.Server("Incorrect current password."))
        assertThat(NetworkResult.Failure.Api(500, null, "\nInvalid `prisma.apiOperator.findMany()` invocation").toUiError()).isEqualTo(UiError.Unknown)
        assertThat(NetworkResult.Failure.NoInternet.toUiError()).isEqualTo(UiError.NoInternet)
        assertThat(safeServerMessage("x".repeat(200))).isNull()
        // UAT recharge, 4 Nov: provider internals with server IPs never reach the screen.
        assertThat(safeServerMessage("LOGIN FAILED FROM IP 43.242.227.100 AGAINST 180.179.212.217")).isNull()
        assertThat(safeServerMessage("api not responding !")).isNull()
        assertThat(safeServerMessage("Transaction Success (Mock Response)")).isNull()
        assertThat(safeServerMessage("Minimum transfer amount is ₹10")).isEqualTo("Minimum transfer amount is ₹10")
        assertThat(safeServerMessage("Insufficient balance: ₹ 1,023.00")).isEqualTo("Insufficient balance: ₹ 1,023.00")
    }
}

class LogRedactorTest {
    private val r = com.asl.wallet.core.network.inspect.LogRedactor
    private fun json(s: String) = r.redact(s.toByteArray(), "application/json; charset=utf-8")

    @Test fun `login and tokens - secrets removed, contact masked`() {
        val out = json("""{"email":"ajay.asltech@gmail.com","password":"Test@123","data":{"accessToken":"eyJhbGciOi.eyJzdWIi.sig","refreshToken":"abc","userpass":"${'$'}2b${'$'}10hash","phone":"9798143077"}}""")
        assertThat(out).doesNotContain("Test@123")
        assertThat(out).doesNotContain("eyJhbGciOi")
        assertThat(out).doesNotContain("10hash")
        assertThat(out).doesNotContain("9798143077")
        assertThat(out).contains("aj•••@gmail.com")
        assertThat(out).contains("97XXX XX077")
    }

    @Test fun `AEPS transaction - PID and Aadhaar never kept`() {
        val out = json("""{"aadhaarNo":"234123412346","txnType":"CW","mobile":"9876543210","bankiin":"607094","amount":"2000",
            "biometricData":"<PidData><Resp errCode=\"0\"/><Data>SECRETBIOMETRIC</Data></PidData>","otp_transation_id":"FP1"}""")
        assertThat(out).doesNotContain("234123412346")
        assertThat(out).doesNotContain("SECRETBIOMETRIC")
        assertThat(out).contains("XXXX XXXX 2346")
        assertThat(out).contains("\"txnType\": \"CW\"")
        assertThat(out).contains("607094")       // bank IIN stays: needed to debug
        assertThat(out).contains("\"amount\": \"2000\"")
    }

    @Test fun `free text, me response and change-password OTP`() {
        val out = json("""{"data":{"aadharno":"234123412346","panno":"IRAPK5045N","otp":"482913","transdesc":"CHARGES FOR VERIFY (A/C : 0185104000179539)","regno":6044}}""")
        assertThat(out).doesNotContain("234123412346")
        assertThat(out).doesNotContain("IRAPK5045N")
        assertThat(out).doesNotContain("482913")
        assertThat(out).doesNotContain("0185104000179539")
        assertThat(out).contains("6044")
    }

    @Test fun `multipart onboarding - fields masked, image hidden`() {
        val b = "--B\r\nContent-Disposition: form-data; name=\"aadharNo\"\r\nContent-Type: text/plain\r\n\r\n234123412346\r\n" +
            "--B\r\nContent-Disposition: form-data; name=\"firstName\"\r\n\r\nAjay\r\n" +
            "--B\r\nContent-Disposition: form-data; name=\"panImage\"; filename=\"p.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n"
        val bytes = b.toByteArray() + ByteArray(600) { (it % 7).toByte() } + "\r\n--B--".toByteArray()
        val out = r.redact(bytes, "multipart/form-data; boundary=B")
        assertThat(out).doesNotContain("234123412346")
        assertThat(out).contains("XXXX XXXX 2346")
        assertThat(out).contains("Ajay")
        assertThat(out).startsWith("[binary parts hidden]")
    }

    @Test fun `binary, empty, broken JSON and bearer text are safe`() {
        assertThat(r.redact(byteArrayOf(1, 2, 3), "image/jpeg")).isEqualTo("[binary body, 3 bytes]")
        assertThat(r.redact(ByteArray(0), "application/json")).isEmpty()
        assertThat(r.redact("""{"password":"x" broken""".toByteArray(), "application/json")).isEqualTo("""{"password": "[redacted]" broken""")
        // Truncated body (Chucker cuts long bodies): secrets and a cut-off PID still go.
        val cut = r.redact("""{"aadhaarNo":"234123412346","otp":"4829","biometricData":"<PidData><Data>SECRET""".toByteArray(), "application/json")
        assertThat(cut).doesNotContain("234123412346"); assertThat(cut).doesNotContain("4829"); assertThat(cut).doesNotContain("SECRET")
        assertThat(r.redactText("Authorization: Bearer abc.def.ghi")).isEqualTo("Authorization: Bearer [redacted]")
    }
}
