package com.asl.wallet.core.testing

import com.asl.wallet.core.common.session.SessionTokens
import com.asl.wallet.core.common.session.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Replaces Dispatchers.Main for ViewModel tests: `@get:Rule val main = MainDispatcherRule()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** In-memory [TokenStore] for tests. */
class InMemoryTokenStore(initial: SessionTokens? = null) : TokenStore {
    val tokens = MutableStateFlow(initial)
    override suspend fun read() = tokens.value
    override suspend fun save(tokens: SessionTokens) { this.tokens.value = tokens }
    override suspend fun clear() { tokens.value = null }
    override val isSignedIn: Flow<Boolean> = tokens.map { it != null }
}
