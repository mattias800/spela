package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.repository.GameRepository
import com.spela.player.domain.usecase.*
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val fakeGameRepo = FakeGameRepository()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GameListViewModel {
        val scope = CoroutineScope(testDispatcher)
        return GameListViewModel(
            getConsolesUseCase = GetConsolesUseCase(fakeGameRepo),
            getGamesForConsoleUseCase = GetGamesForConsoleUseCase(fakeGameRepo),
            searchGamesUseCase = SearchGamesUseCase(fakeGameRepo),
            getRecentGamesUseCase = GetRecentGamesUseCase(fakeGameRepo),
            getFavoriteGamesUseCase = GetFavoriteGamesUseCase(fakeGameRepo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(fakeGameRepo),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue(state.consoles.isEmpty())
        assertTrue(state.games.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun loadDashboardPopulatesState() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadDashboard)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.consoles.size)
        assertEquals(1, state.recentGames.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadConsolesPopulatesConsoles() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.consoles.size)
        assertEquals("NES", state.consoles[0].name)
        assertEquals("SNES", state.consoles[1].name)
    }

    @Test
    fun selectConsoleLoadsGames() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.SelectConsole("1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("1", state.selectedConsoleId)
        assertEquals(2, state.games.size)
    }

    @Test
    fun searchFindsGames() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(GameListIntent.Search("Mario"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Mario", state.searchQuery)
        assertEquals(1, state.games.size)
        assertEquals("Super Mario Bros.", state.games[0].title)
    }

    @Test
    fun errorStateOnFailure() = runTest(testDispatcher) {
        fakeGameRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeGameRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(GameListIntent.LoadConsoles)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(GameListIntent.DismissError)
        assertNull(vm.state.value.error)
    }
}

class FakeGameRepository : GameRepository {
    var shouldFail = false

    private val consoles = listOf(
        Console("1", "NES", "NES", 10),
        Console("2", "SNES", "SNES", 5),
    )

    private val games = listOf(
        Game("1", "Super Mario Bros.", "1", "NES", fileSize = 40960, fileName = "smb.nes"),
        Game("2", "The Legend of Zelda", "1", "NES", fileSize = 131072, fileName = "zelda.nes"),
        Game("3", "Chrono Trigger", "2", "SNES", fileSize = 4194304, fileName = "ct.sfc"),
    )

    override suspend fun getConsoles(): Result<List<Console>> {
        return if (shouldFail) Result.failure(Exception("Network error")) else Result.success(consoles)
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.consoleId == consoleId })
    }

    override suspend fun getAllGames(): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error")) else Result.success(games)
    }

    override suspend fun searchGames(query: String): Result<List<Game>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(games.filter { it.title.contains(query, ignoreCase = true) })
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> {
        val game = games.find { it.id == gameId }
            ?: return Result.failure(Exception("Not found"))
        return Result.success(GameDetail(game))
    }

    override suspend fun getRecentGames(): Result<List<Game>> {
        return Result.success(games.take(1))
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> {
        return Result.success(emptyList())
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = Result.success(Unit)
    override suspend fun removeFavorite(gameId: String): Result<Unit> = Result.success(Unit)
}
