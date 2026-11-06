package com.asl.wallet.feature.auth

import app.cash.turbine.test
import com.asl.wallet.core.common.session.SignOutCleanup
import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.SignOutReason
import com.asl.wallet.core.network.interceptor.SessionEvents
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.core.security.lock.LockState
import com.asl.wallet.core.testing.InMemoryTokenStore
import com.asl.wallet.core.testing.MainDispatcherRule
import com.asl.wallet.feature.auth.data.AuthApi
import com.asl.wallet.feature.auth.data.AuthLocalStore
import com.asl.wallet.feature.auth.data.AuthRepository
import com.asl.wallet.feature.auth.data.AuthRepositoryImpl
import com.asl.wallet.feature.auth.data.safeServerMessage
import com.asl.wallet.feature.auth.domain.ActionResult
import com.asl.wallet.feature.auth.domain.AuthError
import com.asl.wallet.feature.auth.domain.LoginId
import com.asl.wallet.feature.auth.domain.LoginOutcome
import com.asl.wallet.feature.auth.domain.User
import com.asl.wallet.feature.auth.session.SessionManager
import com.asl.wallet.feature.auth.session.SessionState
import com.asl.wallet.feature.auth.ui.AuthEvent
import com.asl.wallet.feature.auth.ui.ForgotPasswordViewModel
import com.asl.wallet.feature.auth.ui.ForgotStep
import com.asl.wallet.feature.auth.ui.LoginViewModel
import com.asl.wallet.feature.auth.ui.OtpViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

private val testJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

class FakeLocal : AuthLocalStore {
    var user: User? = null; var lastId: String? = null; var lang = false; var lock = false
    override suspend fun cachedUser() = user
    override suspend fun saveUser(user: User) { this.user = user }
    override suspend fun lastLoginId() = lastId
    override suspend fun saveLastLoginId(id: String) { lastId = id }
    override suspend fun isLanguageChosen() = lang
    override suspend fun setLanguageChosen() { lang = true }
    override suspend fun isAppLockSetupDone() = lock
    override suspend fun setAppLockSetupDone() { lock = true }
    override suspend fun clearSession() { user = null }
}

/** Response bodies copied from the real UAT API (trimmed). Note the sensitive fields the app must ignore. */
private const val LOGIN_OK = """{"status":true,"statusCode":200,"message":"User login successful.","data":{"requiresOtp":false,"userType":"Retailer",
 "data":{"id":42,"regno":1042,"fname":"Ajay","lname":"Das","phone":"9800000000","email":"ajay@example.com","company_name":"Ajay Store","kyc_status":0,
 "userpass":"${'$'}argon2id${'$'}secret","aadharno":"999941057058","panno":"ABCPS1234K","apitoken":"x"},
 "accessToken":"acc-1","refreshToken":"ref-1"}}"""
private const val LOGIN_OTP = """{"status":true,"statusCode":200,"message":"OTP sent.","data":{"requiresOtp":true,"userType":"Retailer"}}"""
private const val WRONG_PW = """{"status":false,"statusCode":401,"message":"Invalid credentials. 4 attempt(s) remaining before account lockout."}"""

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AuthRepositoryImpl
    private val tokens = InMemoryTokenStore()
    private val local = FakeLocal()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(testJson.asConverterFactory("application/json".toMediaType())).build().create(AuthApi::class.java)
        repo = AuthRepositoryImpl(api, testJson, tokens, local)
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `email login saves tokens and a minimal user, never secrets`() = runTest {
        server.enqueue(MockResponse().setBody(LOGIN_OK))
        val r = repo.login(LoginId.Email("ajay@example.com"), "Test@123")
        assertThat(r).isInstanceOf(LoginOutcome.SignedIn::class.java)
        val user = (r as LoginOutcome.SignedIn).user
        assertThat(user.name).isEqualTo("Ajay Das")
        assertThat(user.shopName).isEqualTo("Ajay Store")
        assertThat(user.toString()).doesNotContain("999941057058")
        assertThat(user.toString()).doesNotContain("argon2")
        assertThat(tokens.tokens.value).isEqualTo(SessionTokens("acc-1", "ref-1"))
        assertThat(local.lastId).isEqualTo("ajay@example.com")
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).isEqualTo("""{"email":"ajay@example.com","password":"Test@123"}""") // no phone:null, no usertype
    }

    @Test fun `mobile login sends phone`() = runTest {
        server.enqueue(MockResponse().setBody(LOGIN_OK))
        repo.login(LoginId.Mobile("9800000000"), "pw")
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"phone":"9800000000","password":"pw"}""")
    }

    @Test fun `wrong password keeps the server message from the 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(WRONG_PW))
        val r = repo.login(LoginId.Mobile("9800000000"), "bad")
        assertThat(r).isEqualTo(LoginOutcome.Failed(AuthError.Server("Invalid credentials. 4 attempt(s) remaining before account lockout.")))
        assertThat(tokens.tokens.value).isNull()
    }

    @Test fun `locked account is detected`() = runTest {
        server.enqueue(MockResponse().setResponseCode(423).setBody("""{"status":false,"message":"Account locked. Try again after 30 minutes."}"""))
        assertThat(repo.login(LoginId.Mobile("9800000000"), "bad")).isEqualTo(LoginOutcome.Locked("Account locked. Try again after 30 minutes."))
    }

    @Test fun `otp flow sends user_id, password and otp, then clears the password`() = runTest {
        server.enqueue(MockResponse().setBody(LOGIN_OTP))
        server.enqueue(MockResponse().setBody(LOGIN_OK))
        assertThat(repo.login(LoginId.Mobile("9800000000"), "Test@123")).isEqualTo(LoginOutcome.OtpRequired)
        assertThat(repo.hasPendingOtp()).isTrue()
        assertThat(repo.verifyOtp("1234")).isInstanceOf(LoginOutcome.SignedIn::class.java)
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"user_id":"9800000000","password":"Test@123","otp":"1234"}""")
        assertThat(repo.hasPendingOtp()).isFalse()
    }

    @Test fun `pending otp expires after 5 minutes`() = runTest {
        var t = 0L
        repo.now = { t }
        server.enqueue(MockResponse().setBody(LOGIN_OTP))
        repo.login(LoginId.Mobile("9800000000"), "pw")
        t += 5 * 60_000 + 1
        assertThat(repo.verifyOtp("1234")).isEqualTo(LoginOutcome.Failed(AuthError.OtpSessionExpired))
    }

    @Test fun `internal server errors are never shown to the user`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody(
            """{"status":false,"statusCode":500,"message":"Invalid `prisma.servicesSetting.findMany()` invocation: The column services_setting.tenant_id does not exist"}""",
        ))
        assertThat(repo.forgotPassword("9800000000")).isEqualTo(ActionResult.Failed(AuthError.Unknown))
    }

    @Test fun `reset password sends both password fields`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":true,"statusCode":200,"message":"Password reset successfully."}"""))
        assertThat(repo.resetPassword("9800000000", "1234", "Newpass1")).isEqualTo(ActionResult.Ok)
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo(
            """{"phone":"9800000000","otp":"1234","new_password":"Newpass1","confirm_password":"Newpass1"}""",
        )
    }
}

class LoginIdTest {
    @Test fun `parses mobile and email`() {
        assertThat(LoginId.parse("98765 43210")).isEqualTo(LoginId.Mobile("9876543210"))
        assertThat(LoginId.parse("+91 9876543210")).isEqualTo(LoginId.Mobile("9876543210"))
        assertThat(LoginId.parse("Ajay.ASLTech@Gmail.com")).isEqualTo(LoginId.Email("ajay.asltech@gmail.com"))
        assertThat(LoginId.parse("12345")).isNull()
        assertThat(LoginId.parse("ajay@")).isNull()
        assertThat(LoginId.parse("5876543210")).isNull()
    }

    @Test fun `server message filter`() {
        assertThat(safeServerMessage("Invalid credentials. 4 attempt(s) remaining")).isNotNull()
        assertThat(safeServerMessage("Invalid `prisma.x.findMany()` invocation")).isNull()
        assertThat(safeServerMessage("Validation Error: password: Invalid input")).isNull()
    }
}

/** Scriptable fake for ViewModel tests. */
class FakeRepo : AuthRepository {
    var loginResult: LoginOutcome = LoginOutcome.OtpRequired
    var verifyResults = ArrayDeque<LoginOutcome>()
    var pending = true
    var resendCalls = 0
    var forgotResult: ActionResult = ActionResult.Ok
    var resetResult: ActionResult = ActionResult.Ok
    override suspend fun login(id: LoginId, password: String) = loginResult
    override suspend fun verifyOtp(otp: String) = verifyResults.removeFirst()
    override suspend fun resendOtp(): ActionResult { resendCalls++; return ActionResult.Ok }
    override fun hasPendingOtp() = pending
    override fun clearPending() { pending = false }
    override suspend fun forgotPassword(mobile: String) = forgotResult
    override suspend fun resetPassword(mobile: String, otp: String, newPassword: String) = resetResult
    override suspend fun logout() {}
}

private val demoUser = User(1, 1004, "Ajay Das", "Ajay Store", "9800000000", "a@b.co", "Retailer", false)

class AuthViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeRepo()
    private val local = FakeLocal()
    private val lock = AppLockManager()
    private var cleaned = 0
    private fun session(scope: TestScope, tokens: InMemoryTokenStore = InMemoryTokenStore()) =
        SessionManager(tokens, local, repo, lock, setOf(SignOutCleanup { cleaned++ }), SessionEvents(), scope.backgroundScope)

    @Test fun `logout runs every feature cleanup and exposes no user`() = runTest {
        val s = session(this)
        s.onSignedIn(demoUser)
        runCurrent()
        assertThat(s.user.value?.regno).isEqualTo(1004L)
        s.logout()
        runCurrent()
        assertThat(cleaned).isEqualTo(1)
        assertThat(s.user.value).isNull()
        assertThat(lock.isSessionActive).isFalse()
    }

    @Test fun `login button needs a valid id and a password`() = runTest {
        val vm = LoginViewModel(repo, session(this), local)
        vm.onLoginIdChange("12345"); vm.onPasswordChange("x")
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onLoginIdChange("9876543210")
        assertThat(vm.state.value.canSubmit).isTrue()
    }

    @Test fun `successful login starts the session and asks for app lock setup the first time`() = runTest {
        repo.loginResult = LoginOutcome.SignedIn(demoUser)
        val s = session(this)
        val vm = LoginViewModel(repo, s, local)
        vm.onLoginIdChange("ajay@example.com"); vm.onPasswordChange("Test@123")
        vm.events.test {
            vm.submit()
            assertThat(awaitItem()).isEqualTo(AuthEvent.SignedIn(needsLockSetup = true))
        }
        assertThat(s.state.value).isEqualTo(SessionState.SignedIn(demoUser))
        assertThat(lock.isSessionActive).isTrue()
        assertThat(vm.state.value.password).isEmpty()
    }

    @Test fun `wrong password clears the field and shows the error`() = runTest {
        repo.loginResult = LoginOutcome.Failed(AuthError.Server("Invalid credentials. 3 attempt(s) remaining"))
        val vm = LoginViewModel(repo, session(this), local)
        vm.onLoginIdChange("9876543210"); vm.onPasswordChange("bad"); vm.submit()
        assertThat(vm.state.value.password).isEmpty()
        assertThat(vm.state.value.error).isEqualTo(AuthError.Server("Invalid credentials. 3 attempt(s) remaining"))
    }

    @Test fun `prefills the last login id`() = runTest {
        local.lastId = "9876543210"
        assertThat(LoginViewModel(repo, session(this), local).state.value.loginId).isEqualTo("9876543210")
    }

    @Test fun `typing 4 digits verifies automatically`() = runTest {
        repo.verifyResults.add(LoginOutcome.SignedIn(demoUser))
        local.lock = true
        val vm = OtpViewModel(repo, session(this), local)
        vm.events.test {
            vm.onOtpChange("12a34")
            assertThat(awaitItem()).isEqualTo(AuthEvent.SignedIn(needsLockSetup = false))
        }
    }

    @Test fun `three wrong codes start a 60 second cool-down`() = runTest {
        repeat(3) { repo.verifyResults.add(LoginOutcome.Failed(AuthError.Server("Invalid OTP"))) }
        val vm = OtpViewModel(repo, session(this), local)
        repeat(3) { vm.onOtpChange("1111") }
        assertThat(vm.state.value.triesLeft).isEqualTo(0)
        assertThat(vm.state.value.coolDown).isGreaterThan(0)
        vm.onOtpChange("2222") // ignored during cool-down
        assertThat(repo.verifyResults).isEmpty()
        advanceTimeBy(61_000)
        assertThat(vm.state.value.triesLeft).isEqualTo(3)
    }

    @Test fun `resend only works after the 60 second timer`() = runTest {
        val vm = OtpViewModel(repo, session(this), local)
        vm.resend()
        assertThat(repo.resendCalls).isEqualTo(0)
        advanceTimeBy(61_000)
        vm.resend()
        assertThat(repo.resendCalls).isEqualTo(1)
        assertThat(vm.state.value.resendIn).isEqualTo(60)
    }

    @Test fun `otp screen without a pending login goes back`() = runTest {
        repo.pending = false
        val vm = OtpViewModel(repo, session(this), local)
        vm.events.test { assertThat(awaitItem()).isEqualTo(AuthEvent.OtpExpired) }
    }

    @Test fun `forgot password needs a good password and matching confirm`() = runTest {
        val vm = ForgotPasswordViewModel(repo, local)
        vm.onMobile("9876543210"); vm.sendCode()
        assertThat(vm.state.value.step).isEqualTo(ForgotStep.Reset)
        vm.onOtp("1234"); vm.onNewPassword("password"); vm.onConfirm("password")
        assertThat(vm.state.value.canSave).isFalse() // weak
        vm.onNewPassword("Password1"); vm.onConfirm("Password2")
        assertThat(vm.state.value.canSave).isFalse() // mismatch
        vm.onConfirm("Password1")
        assertThat(vm.state.value.canSave).isTrue()
        vm.events.test { vm.save(); assertThat(awaitItem()).isEqualTo(AuthEvent.PasswordReset) }
    }

    @Test fun `restoring a saved session opens locked`() = runTest {
        local.user = demoUser
        val s = session(this, InMemoryTokenStore(SessionTokens("a", "r")))
        assertThat(s.restore()).isEqualTo(SessionState.SignedIn(demoUser))
        assertThat(lock.state.value).isEqualTo(LockState.Locked)
    }

    @Test fun `half-saved session is cleaned up`() = runTest {
        val tokens = InMemoryTokenStore(SessionTokens("a", "r"))
        assertThat(session(this, tokens).restore()).isEqualTo(SessionState.SignedOut())
        assertThat(tokens.tokens.value).isNull()
    }

    @Test fun `logout wipes tokens, profile and stops the app lock`() = runTest {
        local.user = demoUser
        val tokens = InMemoryTokenStore(SessionTokens("a", "r"))
        val s = session(this, tokens)
        s.restore(); s.logout()
        assertThat(tokens.tokens.value).isNull()
        assertThat(local.user).isNull()
        assertThat(lock.isSessionActive).isFalse()
        assertThat(s.state.value).isEqualTo(SessionState.SignedOut(SignOutReason.UserRequested))
    }
}
