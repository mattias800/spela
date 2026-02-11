package com.spela.player.presentation.navigation

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.AuthTokens
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.model.User
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.domain.usecase.RestoreSessionUseCase
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

    private fun createViewModel(): NavigationViewModel {
        val scope = CoroutineScope(testDispatcher)
        val restoreSessionUseCase = RestoreSessionUseCase(
            authRepository = NoSessionAuthRepository(),
            serverRepository = NoSessionServerRepository(),
            apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager()),
        )
        return NavigationViewModel(
            restoreSessionUseCase = restoreSessionUseCase,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun goBackPopsFromBackStack() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))

        assertEquals(SpScreen.Console("nes"), vm.state.value.currentScreen)

        vm.onIntent(NavigationIntent.GoBack)

        assertEquals(SpScreen.Home, vm.state.value.currentScreen)
    }

    @Test
    fun navigateToSetsIsGoingBackFalse() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        assertFalse(vm.state.value.isGoingBack)

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        assertFalse(vm.state.value.isGoingBack)
    }

    @Test
    fun goBackSetsIsGoingBackTrue() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))

        vm.onIntent(NavigationIntent.GoBack)
        assertTrue(vm.state.value.isGoingBack)
    }

    @Test
    fun showOverlayPreservesNavigationState() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        assertTrue(vm.state.value.showInGameOverlay)
        assertEquals(screenBefore, vm.state.value.screenBehindOverlay)
        assertEquals(backStackBefore, vm.state.value.backStackBehindOverlay)
    }

    @Test
    fun hideOverlayRestoresNavigationState() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))
        vm.onIntent(NavigationIntent.HideOverlay)

        assertFalse(vm.state.value.showInGameOverlay)
        assertNull(vm.state.value.overlayGameId)
        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(backStackBefore, vm.state.value.backStack)
        assertNull(vm.state.value.screenBehindOverlay)
        assertTrue(vm.state.value.backStackBehindOverlay.isEmpty())
    }

    @Test
    fun hideOverlayRestoresEvenIfBackStackModifiedDuringGameplay() = runTest(testDispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        vm.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("game1")))

        val screenBefore = vm.state.value.currentScreen
        val backStackBefore = vm.state.value.backStack

        vm.onIntent(NavigationIntent.ShowOverlay("game1"))

        // Simulate back stack corruption during gameplay
        vm.onIntent(NavigationIntent.GoBack)
        vm.onIntent(NavigationIntent.GoBack)

        // HideOverlay should restore the original state
        vm.onIntent(NavigationIntent.HideOverlay)

        assertEquals(screenBefore, vm.state.value.currentScreen)
        assertEquals(backStackBefore, vm.state.value.backStack)
    }

    // Minimal fakes that cause RestoreSessionUseCase to return NoSession

    private class NoSessionServerRepository : ServerRepository {
        override fun observeServers(): Flow<List<ServerConnection>> = flowOf(emptyList())
        override suspend fun getServers(): List<ServerConnection> = emptyList()
        override suspend fun getActiveServer(): ServerConnection? = null
        override suspend fun addServer(name: String, url: String): ServerConnection =
            throw UnsupportedOperationException()
        override suspend fun removeServer(id: String) = throw UnsupportedOperationException()
        override suspend fun setActiveServer(id: String) = throw UnsupportedOperationException()
    }

    private class NoSessionAuthRepository : AuthRepository {
        override suspend fun login(serverUrl: String, username: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun register(serverUrl: String, username: String, email: String, password: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun refreshToken(serverUrl: String, refreshToken: String): Result<AuthTokens> =
            throw UnsupportedOperationException()
        override suspend fun getCurrentUser(): Result<User> = throw UnsupportedOperationException()
        override suspend fun getStoredTokens(): AuthTokens? = null
        override suspend fun storeTokens(tokens: AuthTokens) = throw UnsupportedOperationException()
        override suspend fun clearTokens() = throw UnsupportedOperationException()
        override fun isLoggedIn(): Boolean = false
    }
}
