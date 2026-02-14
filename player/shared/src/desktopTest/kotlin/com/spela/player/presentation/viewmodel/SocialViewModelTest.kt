package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ActivityEvent
import com.spela.player.domain.model.OnlineUser
import com.spela.player.domain.model.OnlineUserGame
import com.spela.player.domain.repository.SocialRepository
import com.spela.player.domain.usecase.GetActivityFeedUseCase
import com.spela.player.domain.usecase.GetOnlineUsersUseCase
import com.spela.player.domain.usecase.GetPublicProfileUseCase
import com.spela.player.presentation.intent.SocialIntent
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
class SocialViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeSocialRepo: FakeSocialRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeSocialRepo = FakeSocialRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SocialViewModel {
        val scope = CoroutineScope(testDispatcher)
        return SocialViewModel(
            getOnlineUsersUseCase = GetOnlineUsersUseCase(fakeSocialRepo),
            getActivityFeedUseCase = GetActivityFeedUseCase(fakeSocialRepo),
            getPublicProfileUseCase = GetPublicProfileUseCase(fakeSocialRepo),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue(state.onlineUsers.isEmpty())
        assertTrue(state.activityEvents.isEmpty())
        assertFalse(state.isLoadingOnline)
        assertFalse(state.isLoadingActivity)
        assertNull(state.error)
    }

    @Test
    fun loadOnlineUsersPopulatesState() = runTest(testDispatcher) {
        fakeSocialRepo.onlineUsers = listOf(
            OnlineUser("1", "alice", null, null),
            OnlineUser("2", "bob", null, OnlineUserGame("g1", "Zelda", null, "SNES")),
        )
        val vm = createViewModel()

        vm.onIntent(SocialIntent.LoadOnlineUsers)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.onlineUsers.size)
        assertEquals("alice", state.onlineUsers[0].username)
        assertEquals("bob", state.onlineUsers[1].username)
        assertNotNull(state.onlineUsers[1].currentGame)
        assertFalse(state.isLoadingOnline)
    }

    @Test
    fun loadActivityFeedPopulatesState() = runTest(testDispatcher) {
        fakeSocialRepo.activityEvents = listOf(
            ActivityEvent("e1", "1", "alice", null, "started_playing", "g1", "Mario", null, "NES", "2026-01-01T00:00:00Z"),
        )
        val vm = createViewModel()

        vm.onIntent(SocialIntent.LoadActivityFeed)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.activityEvents.size)
        assertEquals("started_playing", state.activityEvents[0].eventType)
        assertFalse(state.isLoadingActivity)
    }

    @Test
    fun refreshAllLoadsOnlineUsersAndActivity() = runTest(testDispatcher) {
        fakeSocialRepo.onlineUsers = listOf(
            OnlineUser("1", "alice", null, null),
        )
        fakeSocialRepo.activityEvents = listOf(
            ActivityEvent("e1", "1", "alice", null, "favorited_game", "g1", "Zelda", null, "SNES", "2026-01-01T00:00:00Z"),
        )
        val vm = createViewModel()

        vm.onIntent(SocialIntent.RefreshAll)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.onlineUsers.size)
        assertEquals(1, state.activityEvents.size)
        assertFalse(state.isLoadingOnline)
        assertFalse(state.isLoadingActivity)
    }

    @Test
    fun loadOnlineUsersFailureSetsError() = runTest(testDispatcher) {
        fakeSocialRepo.shouldFail = true
        val vm = createViewModel()

        vm.onIntent(SocialIntent.LoadOnlineUsers)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.onlineUsers.isEmpty())
        assertNotNull(state.error)
        assertFalse(state.isLoadingOnline)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeSocialRepo.shouldFail = true
        val vm = createViewModel()

        vm.onIntent(SocialIntent.LoadOnlineUsers)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(SocialIntent.DismissError)
        assertNull(vm.state.value.error)
    }
}

private class FakeSocialRepository : SocialRepository {
    var shouldFail = false
    var onlineUsers: List<OnlineUser> = emptyList()
    var activityEvents: List<ActivityEvent> = emptyList()

    override suspend fun getOnlineUsers(): Result<List<OnlineUser>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(onlineUsers)
    }

    override suspend fun getActivityFeed(page: Int, pageSize: Int): Result<List<ActivityEvent>> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(activityEvents)
    }

    override suspend fun getPublicProfile(userId: String): Result<com.spela.player.domain.model.PublicProfile> {
        return if (shouldFail) Result.failure(Exception("Network error"))
        else Result.failure(Exception("Not implemented in fake"))
    }
}
