package com.spela.player.presentation.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.SaveStateChoice
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.model.User
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.presentation.viewmodel.emulation.StubAchievementsRepository
import com.spela.player.presentation.viewmodel.emulation.StubDownloadRepository
import com.spela.player.presentation.viewmodel.emulation.StubGameRepository
import com.spela.player.presentation.viewmodel.emulation.StubKeyMappingRepository
import com.spela.player.presentation.viewmodel.emulation.StubPendingSaveUploadRepository
import com.spela.player.presentation.viewmodel.emulation.StubPreferencesRepository
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for SettingsViewModel.SetConsoleSaveStatePolicy — the
 * per-console save-state opt-out intent shipped in #821 (#804
 * phase 4b). The intent does an optimistic state update + a
 * background updatePreferences call with rollback on failure;
 * mirrors the selectConsoleShader pattern.
 *
 * VM-level coverage was deferred from #821 because no
 * SettingsViewModel test class existed at the time and adding one
 * for a single intent would have ballooned the PR. This is the
 * minimal scaffold + 4 pinned test cases. Future Settings VM
 * coverage (other intents) can extend or copy this scaffold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelSaveStatePolicyTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private fun createViewModel(
        preferencesRepository: StubPreferencesRepository = StubPreferencesRepository(),
    ): Pair<SettingsViewModel, StubPreferencesRepository> {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val gameRepository = StubGameRepository()
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        val syncEngine = SyncEngine(connectivityMonitor, preferencesRepository, gameRepository, testDispatchers, scope)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        val deviceManager = object : DeviceManager(SpelaDatabase(driver), apiClient) {
            override suspend fun registerWithServer(): Result<Long> = Result.success(1L)
        }
        val vm = SettingsViewModel(
            authRepository = StubAuthRepository(),
            downloadRepository = StubDownloadRepository(),
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            serverRepository = StubServerRepository(),
            achievementsRepository = StubAchievementsRepository(),
            keyMappingRepository = StubKeyMappingRepository(),
            deviceManager = deviceManager,
            syncEngine = syncEngine,
            connectivityMonitor = connectivityMonitor,
            apiClient = apiClient,
            pendingUploadRepository = StubPendingSaveUploadRepository(),
            dispatchers = testDispatchers,
            scope = scope,
        )
        return vm to preferencesRepository
    }

    @Test
    fun loadSettingsPopulatesConsoleSaveStatePoliciesFromPreferences() = runTest(testDispatcher) {
        val prefs = StubPreferencesRepository().apply {
            preferencesResult = Result.success(
                UserPreferences(consoleSaveStatePolicies = mapOf("snes" to SaveStateChoice.Disabled))
            )
        }
        val (vm, _) = createViewModel(prefs)

        vm.onIntent(SettingsIntent.LoadSettings)
        advanceUntilIdle()

        assertEquals(
            mapOf("snes" to SaveStateChoice.Disabled),
            vm.state.value.consoleSaveStatePolicies,
        )
    }

    @Test
    fun setConsoleSaveStatePolicyOptimisticallyUpdatesAndWritesPreference() = runTest(testDispatcher) {
        val (vm, prefs) = createViewModel()

        vm.onIntent(SettingsIntent.SetConsoleSaveStatePolicy("snes", SaveStateChoice.Disabled))
        advanceUntilIdle()

        assertEquals(
            SaveStateChoice.Disabled,
            vm.state.value.consoleSaveStatePolicies["snes"],
            "VM state reflects the choice immediately",
        )
        assertEquals(
            mapOf("snes" to "disabled"),
            prefs.lastConsoleSaveStatePoliciesUpdate,
            "preferences repo received the api-format string",
        )
    }

    @Test
    fun setConsoleSaveStatePolicyNullClearsOverride() = runTest(testDispatcher) {
        val prefs = StubPreferencesRepository().apply {
            preferencesResult = Result.success(
                UserPreferences(consoleSaveStatePolicies = mapOf("snes" to SaveStateChoice.Disabled))
            )
        }
        val (vm, _) = createViewModel(prefs)
        vm.onIntent(SettingsIntent.LoadSettings)
        advanceUntilIdle()
        assertEquals(SaveStateChoice.Disabled, vm.state.value.consoleSaveStatePolicies["snes"])

        vm.onIntent(SettingsIntent.SetConsoleSaveStatePolicy("snes", null))
        advanceUntilIdle()

        assertNull(
            vm.state.value.consoleSaveStatePolicies["snes"],
            "passing null choice removes the entry from the VM map",
        )
        assertEquals(
            mapOf("snes" to ""),
            prefs.lastConsoleSaveStatePoliciesUpdate,
            "the wire format sends the empty string to clear the row server-side",
        )
    }

    @Test
    fun setConsoleSaveStatePolicyConsoleIdLowercased() = runTest(testDispatcher) {
        val (vm, prefs) = createViewModel()

        vm.onIntent(SettingsIntent.SetConsoleSaveStatePolicy("SNES", SaveStateChoice.Enabled))
        advanceUntilIdle()

        assertTrue("snes" in vm.state.value.consoleSaveStatePolicies)
        assertNull(vm.state.value.consoleSaveStatePolicies["SNES"])
        assertEquals(mapOf("snes" to "enabled"), prefs.lastConsoleSaveStatePoliciesUpdate)
    }

    // -- Stubs --

    private class StubServerRepository : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> = flowOf(emptyList())
        override suspend fun getServers(): List<ServerConnection> = emptyList()
        override suspend fun getActiveServer(): ServerConnection? = null
        override suspend fun addServer(name: String, url: String): ServerConnection =
            throw UnsupportedOperationException()
        override suspend fun removeServer(id: String) = throw UnsupportedOperationException()
        override suspend fun setActiveServer(id: String) = throw UnsupportedOperationException()
        override suspend fun validateServer(url: String): Boolean = false
    }

    private class StubAuthRepository : AuthRepository {
        override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun getCurrentUser(): Result<User> =
            Result.failure(IllegalStateException("not configured"))
        override suspend fun getStoredTokens(): AuthTokens? = null
        override suspend fun storeTokens(tokens: AuthTokens) {}
        override suspend fun clearTokens() {}
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override fun isLoggedIn(): Boolean = false
    }
}
