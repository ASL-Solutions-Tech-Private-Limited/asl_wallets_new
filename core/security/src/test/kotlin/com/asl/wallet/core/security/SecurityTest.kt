package com.asl.wallet.core.security

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.security.integrity.DeviceEnvironment
import com.asl.wallet.core.security.integrity.DeviceIntegrityChecker
import com.asl.wallet.core.security.integrity.IntegrityIssue
import com.asl.wallet.core.security.lock.AppLockManager
import com.asl.wallet.core.security.lock.Clock
import com.asl.wallet.core.security.lock.LockState
import com.asl.wallet.core.security.storage.SecureTokenStore
import com.asl.wallet.core.security.storage.StringCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakeClock(var now: Long = 0) : Clock { override fun nowMillis() = now }

class AppLockManagerTest {
    private val clock = FakeClock()
    private val lock = AppLockManager(clock, backgroundTimeoutMs = 30_000, idleTimeoutMs = 300_000).apply { isSessionActive = true }

    @Test fun `short trip to background stays unlocked`() {
        lock.onBackgrounded(); clock.now += 29_000; lock.onForegrounded()
        assertThat(lock.state.value).isEqualTo(LockState.Unlocked)
    }

    @Test fun `30 seconds in background locks`() {
        lock.onBackgrounded(); clock.now += 30_000; lock.onForegrounded()
        assertThat(lock.state.value).isEqualTo(LockState.Locked)
    }

    @Test fun `5 minutes idle locks, even without a touch`() {
        clock.now += 299_000; lock.checkIdle()
        assertThat(lock.state.value).isEqualTo(LockState.Unlocked)
        clock.now += 1_000; lock.checkIdle()
        assertThat(lock.state.value).isEqualTo(LockState.Locked)
    }

    @Test fun `touches keep the app unlocked`() {
        repeat(10) { clock.now += 200_000; lock.onUserInteraction() }
        assertThat(lock.state.value).isEqualTo(LockState.Unlocked)
    }

    @Test fun `unlock resets, and no session means no lock`() {
        lock.lockNow(); assertThat(lock.state.value).isEqualTo(LockState.Locked)
        lock.onUnlocked(); assertThat(lock.state.value).isEqualTo(LockState.Unlocked)
        lock.isSessionActive = false
        lock.onBackgrounded(); clock.now += 60_000; lock.onForegrounded(); lock.lockNow()
        assertThat(lock.state.value).isEqualTo(LockState.Unlocked)
    }
}

private class FakeEnv(
    val files: Set<String> = emptySet(),
    override val buildTags: String? = "release-keys",
    override val fingerprint: String = "Xiaomi/redmi/note13:14/UKQ1/V816:user/release-keys",
    override val hardware: String = "mt6789",
    override val model: String = "Redmi Note 13",
    override val product: String = "note13",
    override val manufacturer: String = "Xiaomi",
    override val isDebuggerConnected: Boolean = false,
    override val isAppDebuggable: Boolean = false,
) : DeviceEnvironment {
    override fun fileExists(path: String) = path in files
}

class DeviceIntegrityTest {
    @Test fun `clean phone has no issues`() {
        val r = DeviceIntegrityChecker(FakeEnv()).check(isReleaseBuild = true)
        assertThat(r.issues).isEmpty()
        assertThat(r.blocksPayments(allowEmulator = false)).isFalse()
    }

    @Test fun `su binary or magisk means rooted and blocks payments`() {
        val r = DeviceIntegrityChecker(FakeEnv(files = setOf("/data/adb/magisk"))).check(true)
        assertThat(r.issues).contains(IntegrityIssue.Rooted)
        assertThat(r.blocksPayments(allowEmulator = true)).isTrue()
    }

    @Test fun `emulator is allowed on uat but blocked on live`() {
        val r = DeviceIntegrityChecker(FakeEnv(hardware = "ranchu", product = "sdk_gphone64_arm64")).check(true)
        assertThat(r.issues).containsExactly(IntegrityIssue.Emulator)
        assertThat(r.blocksPayments(allowEmulator = true)).isFalse()
        assertThat(r.blocksPayments(allowEmulator = false)).isTrue()
    }

    @Test fun `debugger only matters on release builds`() {
        assertThat(DeviceIntegrityChecker(FakeEnv(isDebuggerConnected = true)).check(false).issues).isEmpty()
        assertThat(DeviceIntegrityChecker(FakeEnv(isDebuggerConnected = true)).check(true).issues).contains(IntegrityIssue.DebuggerAttached)
    }
}

/** Reversible fake: proves the store only ever writes the cipher's output to disk. */
private class FakeCipher : StringCipher {
    override fun encrypt(plain: String) = "ENC(" + plain.reversed() + ")"
    override fun decrypt(cipher: String): String {
        require(cipher.startsWith("ENC(")) { "bad" }
        return cipher.removePrefix("ENC(").removeSuffix(")").reversed()
    }
}

class SecureTokenStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher(testScheduler))) {
        tmp.newFile("s.preferences_pb").also { it.delete() }
    }

    @Test fun `round trips tokens and never stores plaintext`() = runTest {
        val ds = store()
        val s = SecureTokenStore(ds, FakeCipher())
        s.save(SessionTokens("access-123", "refresh-456"))
        val raw = ds.data.first()[stringPreferencesKey("session")]!!
        assertThat(raw).startsWith("ENC(")
        assertThat(raw).doesNotContain("access-123")
        assertThat(SecureTokenStore(ds, FakeCipher()).read()).isEqualTo(SessionTokens("access-123", "refresh-456"))
        assertThat(s.isSignedIn.first()).isTrue()
    }

    @Test fun `clear removes the session`() = runTest {
        val ds = store()
        val s = SecureTokenStore(ds, FakeCipher())
        s.save(SessionTokens("a", "r")); s.clear()
        assertThat(s.read()).isNull()
        assertThat(s.isSignedIn.first()).isFalse()
    }

    @Test fun `undecryptable data is wiped instead of crashing`() = runTest {
        val ds = store()
        SecureTokenStore(ds, object : StringCipher {
            override fun encrypt(plain: String) = "garbage"
            override fun decrypt(cipher: String) = error("unused")
        }).save(SessionTokens("a", "r"))
        val fresh = SecureTokenStore(ds, FakeCipher())
        assertThat(fresh.read()).isNull()
        assertThat(ds.data.first()[stringPreferencesKey("session")]).isNull()
    }
}
