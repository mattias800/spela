package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionMember
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.model.UserSearchResult
import com.spela.player.domain.repository.UserRepository
import com.spela.player.domain.repository.UserSearchPage
import com.spela.player.presentation.intent.SharedSessionDetailIntent
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
class SharedSessionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepo: FakeSharedSessionRepo
    private lateinit var fakeUserRepo: FakeUserRepo

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSharedSessionRepo()
        fakeUserRepo = FakeUserRepo()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SharedSessionDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        return SharedSessionDetailViewModel(
            sharedSessionRepository = fakeRepo,
            userRepository = fakeUserRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertNull(state.sharedSession)
        assertTrue(state.saves.isEmpty())
        assertFalse(state.isLoadingSharedSession)
        assertFalse(state.isLoadingSaves)
        assertNull(state.turnToken)
    }

    @Test
    fun loadSharedSessionPopulatesState() = runTest(testDispatcher) {
        fakeRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            members = listOf(
                SharedSessionMember(userId = "1", username = "player", role = "owner"),
                SharedSessionMember(userId = "2", username = "alice", role = "member"),
            ),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LoadSharedSession("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.sharedSession)
        assertEquals("Test Session", state.sharedSession.name)
        assertEquals(2, state.sharedSession.members.size)
        assertFalse(state.isLoadingSharedSession)
    }

    @Test
    fun loadSharedSessionFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LoadSharedSession("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.sharedSession)
        assertNotNull(state.error)
        assertFalse(state.isLoadingSharedSession)
    }

    @Test
    fun loadSavesPopulatesState() = runTest(testDispatcher) {
        fakeRepo.sharedSessionSaves = listOf(
            SharedSessionSave(id = 1, sharedSessionId = "ss1", username = "player", name = "Save 1", fileSize = 4096),
            SharedSessionSave(id = 2, sharedSessionId = "ss1", username = "alice", name = "Auto Save", isAuto = true, fileSize = 4096),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LoadSaves("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.saves.size)
        assertEquals("Save 1", state.saves[0].name)
        assertTrue(state.saves[1].isAuto)
        assertFalse(state.isLoadingSaves)
    }

    @Test
    fun loadSavesFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LoadSaves("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingSaves)
    }

    @Test
    fun takeTurnStoresTurnToken() = runTest(testDispatcher) {
        fakeRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.TakeTurn("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("fake-token", state.turnToken)
        assertFalse(state.isTakingTurn)
    }

    @Test
    fun takeTurnFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.TakeTurn("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.turnToken)
        assertNotNull(state.error)
        assertFalse(state.isTakingTurn)
    }

    @Test
    fun releaseTurnClearsTurnToken() = runTest(testDispatcher) {
        fakeRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()

        // Take turn first
        vm.onIntent(SharedSessionDetailIntent.TakeTurn("ss1"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.turnToken)

        // Release turn
        vm.onIntent(SharedSessionDetailIntent.ReleaseTurn("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.turnToken)
        assertFalse(state.isReleasingTurn)
    }

    @Test
    fun releaseTurnFailureShowsError() = runTest(testDispatcher) {
        val vm = createViewModel()
        // Take turn successfully first
        vm.onIntent(SharedSessionDetailIntent.TakeTurn("ss1"))
        advanceUntilIdle()

        // Now make release fail
        fakeRepo.shouldFail = true
        vm.onIntent(SharedSessionDetailIntent.ReleaseTurn("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isReleasingTurn)
    }

    @Test
    fun inviteUserShowsSuccessMessage() = runTest(testDispatcher) {
        fakeRepo.sharedSessionDetail = SharedSessionDetail(
            id = "ss1",
            name = "Test Session",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.InviteUser("ss1", "bob"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Invitation sent to bob", state.successMessage)
        assertFalse(state.isInviting)
    }

    @Test
    fun inviteUserFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.InviteUser("ss1", "bob"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isInviting)
    }

    @Test
    fun leaveSharedSessionShowsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LeaveSharedSession("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("You left the shared session", state.successMessage)
    }

    @Test
    fun leaveSharedSessionFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LeaveSharedSession("ss1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.LoadSharedSession("ss1"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(SharedSessionDetailIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissSuccessClearsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(SharedSessionDetailIntent.InviteUser("ss1", "bob"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.successMessage)

        vm.onIntent(SharedSessionDetailIntent.DismissSuccess)
        assertNull(vm.state.value.successMessage)
    }
}

private class FakeUserRepo : UserRepository {
    override suspend fun searchUsers(query: String, page: Int, pageSize: Int): Result<UserSearchPage> =
        Result.success(UserSearchPage(data = emptyList(), total = 0, page = page, pageSize = pageSize))

    override suspend fun getRecentPartners(): Result<List<UserSearchResult>> =
        Result.success(emptyList())
}
