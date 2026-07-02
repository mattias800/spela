package com.spela.player.presentation.navigation

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.SyncEngine
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.*
import com.spela.player.domain.repository.*
import com.spela.player.domain.usecase.RestoreSessionUseCase
import com.spela.player.presentation.ui.components.BottomNavTab
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): NavigationViewModel = createViewModelWithFederation()

    /**
     * Builds a NavigationViewModel wired to a fake FederationRepository that
     * returns [connectedConsoles]. Fire NavigationIntent.ResetToHome (the
     * post-login path) to make the VM decide whether to surface the Connected
     * Servers tab (#1435).
     */
    private fun createViewModelWithFederation(
        connectedConsoles: List<ConnectedConsole> = emptyList(),
    ): NavigationViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val restoreSessionUseCase = RestoreSessionUseCase(
            authRepository = NoSessionAuthRepository(),
            serverRepository = NoSessionServerRepository(),
            apiClient = apiClient,
        )
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        val syncEngine = SyncEngine(
            connectivityMonitor = connectivityMonitor,
            preferencesRepository = NoOpPreferencesRepository(),
            gameRepository = NoOpGameRepository(),
            dispatchers = testDispatchers,
            scope = scope,
        )
        return NavigationViewModel(
            restoreSessionUseCase = restoreSessionUseCase,
            connectivityMonitor = connectivityMonitor,
            syncEngine = syncEngine,
            dispatchers = testDispatchers,
            scope = scope,
            federationRepository = FakeFederationRepository(connectedConsoles),
        )
    }

    /** Builds a NavigationViewModel with a NoSession restore + the given
     *  onboarding repo, to exercise the first-run wizard gating (#1448). */
    private fun createViewModelWithOnboarding(
        onboardingRepository: OnboardingRepository,
    ): NavigationViewModel {
        val scope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        val restoreSessionUseCase = RestoreSessionUseCase(
            authRepository = NoSessionAuthRepository(),
            serverRepository = NoSessionServerRepository(),
            apiClient = apiClient,
        )
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        val syncEngine = SyncEngine(
            connectivityMonitor = connectivityMonitor,
            preferencesRepository = NoOpPreferencesRepository(),
            gameRepository = NoOpGameRepository(),
            dispatchers = testDispatchers,
            scope = scope,
        )
        return NavigationViewModel(
            restoreSessionUseCase = restoreSessionUseCase,
            connectivityMonitor = connectivityMonitor,
            syncEngine = syncEngine,
            dispatchers = testDispatchers,
            scope = scope,
            onboardingRepository = onboardingRepository,
        )
    }

    private class FakeOnboarding(dismissed: Boolean) : OnboardingRepository {
        private val keys = mutableSetOf<String>().apply {
            if (dismissed) add(OnboardingHintKeys.FIRST_RUN_WIZARD_COMPLETED)
        }
        override suspend fun isDismissed(key: String): Boolean = key in keys
        override suspend fun markDismissed(key: String) { keys.add(key) }
        override suspend fun clearDismissed(key: String) { keys.remove(key) }
    }

    @Test
    fun firstRunRoutesToOnboardingWizard() = runTest(testDispatcher) {
        val vm = createViewModelWithOnboarding(FakeOnboarding(dismissed = false))
        advanceUntilIdle()
        assertEquals(SpScreen.OnboardingWizard, vm.state.value.currentScreen)
    }

    @Test
    fun completedWizardRoutesToBareAuthScreen() = runTest(testDispatcher) {
        val vm = createViewModelWithOnboarding(FakeOnboarding(dismissed = true))
        advanceUntilIdle()
        assertEquals(SpScreen.ServerConnection, vm.state.value.currentScreen)
    }

    /** Helper: the active tab's stack. */
    private fun NavigationState.activeStack(): List<SpScreen> =
        tabStacks[activeTab] ?: emptyList()

    /** Simulates a logged-in state by navigating to Home from ServerConnection.
     *  This mimics what happens after successful login: NavigateTo(Home). */
    private fun simulateLoggedIn(vm: NavigationViewModel) {
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        // Now the Home tab has [ServerConnection, Home]. GoBack would go to ServerConnection.
        // In the real app, login replaces the screen, but for tests we just need Home as current.
    }

    @Test
    fun navigateToPushesOntoActiveTabStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        simulateLoggedIn(vm)

        val stackSizeBefore = vm.state.value.activeStack().size
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))

        assertEquals(SpScreen.GameDetail("1"), vm.state.value.currentScreen)
        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)
        assertEquals(stackSizeBefore + 1, vm.state.value.activeStack().size)
    }

    @Test
    fun goBackPopsFromActiveTabStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        simulateLoggedIn(vm)

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertEquals(SpScreen.Console("nes"), vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.GoBack)
        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun navigateToSetsIsGoingBackFalse() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertFalse(vm.state.value.isGoingBack)
    }

    @Test
    fun goBackSetsIsGoingBackTrue() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.GoBack)
        assertTrue(vm.state.value.isGoingBack)
    }

    @Test
    fun switchTabPreservesStacks() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Navigate deep on Home tab
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        assertEquals(SpScreen.GameDetail("1"), vm.state.value.currentScreen)

        // Switch to Consoles tab
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)

        // Switch back to Home — stack is preserved
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Home))
        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)
        assertEquals(SpScreen.GameDetail("1"), vm.state.value.currentScreen)
    }

    @Test
    fun reTapActiveTabPopsStackToRootWhenDeep() = runTest(testDispatcher) {
        // Universal tab convention (#846): tapping a tab the user is
        // already on resets that tab's stack to its root. Without this
        // the gesture is a silent no-op and the user is stuck in deep
        // navigation with no obvious way back besides repeated GoBack.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("snes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("17")))
        assertEquals(SpScreen.GameDetail("17"), vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))

        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)
        // The pop should look like a GoBack to the screen-transition
        // animation, not a tab-switch — the user hasn't switched tabs.
        assertTrue(vm.state.value.isGoingBack, "active-tab re-tap renders as a back transition")
        assertFalse(vm.state.value.isTabSwitch, "active-tab re-tap is not a tab switch")
    }

    @Test
    fun reTapActiveTabAtRootIsNoOp() = runTest(testDispatcher) {
        // Edge case from the issue's design: when the stack is already
        // a single root entry, the re-tap should not emit a GoBack
        // animation or otherwise produce observable change.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        val before = vm.state.value

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))

        assertEquals(before, vm.state.value, "no state change when active tab is already at its root")
    }

    @Test
    fun reTapActiveTabPopsOnlyThatTabsStack() = runTest(testDispatcher) {
        // Each tab maintains its own independent stack — re-tapping the
        // active tab must not disturb other tabs' stacks. Regression
        // protection for the SwitchTab branch I just changed.
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("home-deep")))
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("gba")))

        // Re-tap Consoles → only the Consoles stack pops.
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)

        // Switching to Home shows the still-deep Home stack untouched.
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Home))
        assertEquals(SpScreen.GameDetail("home-deep"), vm.state.value.currentScreen)
    }

    @Test
    fun activeTabStaysCorrectThroughDeepNavigation() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Switch to Consoles, navigate deep
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("42")))

        // Active tab should still be CONSOLES, not HOME
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)
        assertEquals(SpScreen.GameDetail("42"), vm.state.value.currentScreen)
    }

    @Test
    fun showOverlayPreservesAllTabStacks() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val tabBefore = vm.state.value.activeTab
        val stacksBefore = vm.state.value.tabStacks

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        assertTrue(vm.state.value.showInGameOverlay)
        assertEquals(tabBefore, vm.state.value.activeTabBehindOverlay)
        assertEquals(stacksBefore, vm.state.value.tabStacksBehindOverlay)
    }

    @Test
    fun hideOverlayRestoresAllTabStacks() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val tabBefore = vm.state.value.activeTab
        val stacksBefore = vm.state.value.tabStacks

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))
        vm.onIntent(NavigationIntent.HideOverlay)

        assertFalse(vm.state.value.showInGameOverlay)
        assertNull(vm.state.value.overlayGameId)
        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(tabBefore, vm.state.value.activeTab)
        assertEquals(stacksBefore, vm.state.value.tabStacks)
    }

    @Test
    fun hideOverlayRestoresEvenIfStackModifiedDuringGameplay() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val stacksBefore = vm.state.value.tabStacks

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        // Simulate modification during gameplay
        vm.onIntent(NavigationIntent.GoBack)

        // HideOverlay should restore the original state
        vm.onIntent(NavigationIntent.HideOverlay)

        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(stacksBefore, vm.state.value.tabStacks)
    }

    @Test
    fun nextSectionCyclesThroughVisibleTabsSkippingConnectedServersByDefault() = runTest(testDispatcher) {
        // With no connected servers, the Connected Servers tab is hidden, so
        // the L/R cycle skips it: Consoles → Collections directly (#1435).
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.EXPLORE, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.COLLECTIONS, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.ACTIVITY, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.SETTINGS, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)
    }

    @Test
    fun connectedServersTabIsHiddenWhenNoConnectedServers() = runTest(testDispatcher) {
        // Logged in, but the connected-server catalog is empty → tab stays hidden.
        val vm = createViewModelWithFederation(connectedConsoles = emptyList())
        advanceUntilIdle()
        vm.onIntent(NavigationIntent.ResetToHome) // post-login refresh path
        advanceUntilIdle()

        assertFalse(vm.state.value.hasConnectedServers)
    }

    @Test
    fun connectedServersTabAppearsInCycleWhenConnectedServersPresent() = runTest(testDispatcher) {
        // A connected server shares games → the VM surfaces the tab after login,
        // and the L/R cycle then includes Connected Servers after Consoles.
        val vm = createViewModelWithFederation(
            connectedConsoles = listOf(ConnectedConsole(console = "snes", count = 3)),
        )
        advanceUntilIdle()
        vm.onIntent(NavigationIntent.ResetToHome) // post-login refresh path
        advanceUntilIdle()

        assertTrue(vm.state.value.hasConnectedServers)

        vm.onIntent(NavigationIntent.NextSection) // Explore
        vm.onIntent(NavigationIntent.NextSection) // Consoles
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.CONNECTED_SERVERS, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.COLLECTIONS, vm.state.value.activeTab)
    }

    @Test
    fun previousSectionCyclesBackward() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.PreviousSection)
        assertEquals(BottomNavTab.SETTINGS, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.PreviousSection)
        assertEquals(BottomNavTab.ACTIVITY, vm.state.value.activeTab)
    }

    @Test
    fun sectionCyclingPreservesTabStacks() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Navigate deep on Home tab
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        val homeStack = vm.state.value.tabStacks[BottomNavTab.HOME]

        // Cycle to next section
        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.EXPLORE, vm.state.value.activeTab)

        // Home stack should be preserved
        assertEquals(homeStack, vm.state.value.tabStacks[BottomNavTab.HOME])
    }

    @Test
    fun goBackAtConsolesTabRootIsNoOp() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        simulateLoggedIn(vm)

        // Switch to Consoles tab (empty stack, just root)
        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Consoles))
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)

        // GoBack at a tab root does nothing — stays on the tab, never jumps to Home (#1372).
        vm.onIntent(NavigationIntent.GoBack)
        assertEquals(BottomNavTab.CONSOLES, vm.state.value.activeTab)
        assertEquals(SpScreen.Consoles, vm.state.value.currentScreen)
    }

    @Test
    fun goBackAtCollectionsTabRootIsNoOp() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.SwitchTab(SpScreen.Collections))
        assertEquals(BottomNavTab.COLLECTIONS, vm.state.value.activeTab)

        vm.onIntent(NavigationIntent.GoBack)
        assertEquals(BottomNavTab.COLLECTIONS, vm.state.value.activeTab)
    }

    @Test
    fun nextSectionSetsIsGoingBackFalse() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NextSection)
        assertFalse(vm.state.value.isGoingBack)
    }

    @Test
    fun previousSectionSetsIsGoingBackTrue() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.PreviousSection)
        assertTrue(vm.state.value.isGoingBack)
    }

    @Test
    fun nextSectionFromDeepScreenPreservesStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        // Navigate deep on Home tab
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        assertEquals(BottomNavTab.HOME, vm.state.value.activeTab)

        // NextSection switches to Explore tab
        vm.onIntent(NavigationIntent.NextSection)
        assertEquals(BottomNavTab.EXPLORE, vm.state.value.activeTab)
        assertEquals(SpScreen.Explore, vm.state.value.currentScreen)

        // Home stack is preserved with the deep navigation
        val homeStack = vm.state.value.tabStacks[BottomNavTab.HOME]!!
        assertEquals(3, homeStack.size)
        assertEquals(SpScreen.GameDetail("1"), homeStack.last())
    }

    // Minimal fakes that cause RestoreSessionUseCase to return NoSession

    private class NoSessionServerRepository : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> = flowOf(emptyList())
        override suspend fun getServers(): List<ServerConnection> = emptyList()
        override suspend fun getActiveServer(): ServerConnection? = null
        override suspend fun addServer(name: String, url: String): ServerConnection =
            error("NoSessionServerRepository: addServer not used in this test")
        override suspend fun removeServer(id: String) {}
        override suspend fun setActiveServer(id: String) {}
        override suspend fun validateServer(url: String): Boolean = false
    }

    private class NoSessionAuthRepository : AuthRepository {
        private fun unsupported(): NotImplementedError = NotImplementedError("NoSessionAuthRepository: not used in this test")
        override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            Result.failure(unsupported())
        override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> =
            Result.failure(unsupported())
        override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> =
            Result.failure(unsupported())
        override suspend fun getCurrentUser(): Result<User> = Result.failure(unsupported())
        override suspend fun getStoredTokens(): AuthTokens? = null
        override suspend fun storeTokens(tokens: AuthTokens) {}
        override suspend fun clearTokens() {}
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override fun isLoggedIn(): Boolean = false
    }

    // Minimal stubs for SyncEngine dependencies (never actually called in these tests)

    private class NoOpPreferencesRepository : PreferencesRepository {
        override suspend fun getPreferences() = Result.success(UserPreferences())
        override suspend fun updatePreferences(showPerformanceOverlay: Boolean?, autoSaveEnabled: Boolean?, autoLoadSaveEnabled: Boolean?, autoUpdateCoresEnabled: Boolean?, selectedShader: String?, selectedTheme: String?, consoleShaders: Map<String, String>?, consoleRenderScales: Map<String, String>?, consoleSaveStatePolicies: Map<String, String>?, gameSaveStatePolicies: Map<String, String>?, defaultSecondScreenPage: String?) = Result.success(UserPreferences())
        override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
        override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) {}
        override fun getAllDeviceShaderOverrides() = emptyMap<String, ShaderPreset>()
        override suspend fun syncDeviceShaderOverrides() {}
        override suspend fun pushDeviceShaderOverridesToServer() {}
        override suspend fun resolveShader(consoleId: String) = ShaderPreset.NONE
        override suspend fun syncKeyMappingsFromServer() {}
        override suspend fun pushKeyMappingsToServer() {}
        override suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>) {}
        override suspend fun deleteGameKeyMappingOnServer(gameId: String) {}
        override suspend fun syncGameKeyMappingFromServer(gameId: String) {}
        override fun getOrientationLock(): String = "auto"
        override fun setOrientationLock(mode: String) {}
        override fun getControlTab(consoleId: String): String =
            if (consoleId.lowercase() == "scummvm") "trackpad" else "gamepad"
        override fun setControlTab(consoleId: String, tab: String) {}
        override fun getConsoleListGrouping(): String = "generation"
        override fun getConfirmButtonConvention(): String = "xbox"
        override fun setConfirmButtonConvention(convention: String) {}
        override fun setConsoleListGrouping(grouping: String) {}
    }

    private class NoOpGameRepository : GameRepository {
        override suspend fun getConsoles() = Result.success(emptyList<Console>())
        override suspend fun getGamesForConsole(consoleId: String) = Result.success(emptyList<Game>())
        override suspend fun getAllGames() = Result.success(emptyList<Game>())
        override suspend fun searchGames(query: String, consoleId: String?, sortBy: String?, sortOrder: String?) = Result.success(emptyList<Game>())
        override suspend fun getGameDetail(gameId: String) = Result.success(GameDetail(game = Game(id = gameId, title = "Test", consoleId = "nes")))
        override suspend fun getRecentGames() = Result.success(emptyList<Game>())
        override suspend fun getFavoriteGames() = Result.success(emptyList<Game>())
        override suspend fun addFavorite(gameId: String) = Result.success(Unit)
        override suspend fun removeFavorite(gameId: String) = Result.success(Unit)
        override suspend fun getPlayLaterGames() = Result.success(emptyList<Game>())
        override suspend fun addToPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun removeFromPlayLater(gameId: String) = Result.success(Unit)
        override suspend fun getTopRatedGames(consoleId: String) = Result.success(emptyList<TopRatedGame>())
    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> = Result.success(emptyList())
        override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> = Result.success(emptyList())
        override suspend fun getLongestGames(): Result<List<LongestGame>> = Result.success(emptyList())
        override suspend fun getSimilarGames(gameId: String) = Result.success(emptyList<SimilarGame>())
        override suspend fun getDeveloperGames(gameId: String) = Result.success(emptyList<DeveloperGame>())
        override suspend fun getRecentlyAddedGames(): Result<List<Game>> = Result.success(emptyList())
        override suspend fun getGamesForConsolePaginated(consoleId: String, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun getAllGamesPaginated(page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
        override suspend fun searchGamesPaginated(query: String, consoleId: String?, sortBy: String?, sortOrder: String?, page: Int, pageSize: Int, hidePreRelease: Boolean, grouped: Boolean) = Result.success(PaginatedResult<Game>(emptyList(), 0, page, pageSize))
    }

    // Only getConnectedConsoles drives the Connected Servers tab gate (#1435);
    // the rest are unused stubs for these tests.
    private class FakeFederationRepository(
        private val connectedConsoles: List<ConnectedConsole>,
    ) : FederationRepository {
        override suspend fun getConnectedConsoles() = Result.success(connectedConsoles)
        override suspend fun getGamesForConsole(console: String) = Result.success(emptyList<RemoteGame>())
        override suspend fun getRemoteGame(key: String) = Result.success<RemoteGame?>(null)
        override suspend fun startImport(key: String, title: String, console: String) =
            Result.failure<ImportJob>(NotImplementedError("FakeFederationRepository: not used in this test"))
        override suspend fun listImports() = Result.success(emptyList<ImportJob>())
        override suspend fun getAggregatedPresence() = Result.success(emptyList<FriendPresence>())
        override suspend fun getAggregatedStats(metric: MeshStatMetric) = Result.success(emptyList<MeshStat>())
        override suspend fun getAggregatedAchievers() = Result.success(emptyList<MeshAchiever>())
    }

}
