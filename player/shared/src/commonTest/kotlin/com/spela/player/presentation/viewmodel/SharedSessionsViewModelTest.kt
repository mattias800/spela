package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.SharedSession
import com.spela.player.domain.model.SharedSessionDetail
import com.spela.player.domain.model.SharedSessionInvitation
import com.spela.player.domain.model.SharedSessionSave
import com.spela.player.domain.repository.SharedSessionRepository
import com.spela.player.presentation.intent.SharedSessionIntent
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
class SharedSessionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepo: FakeSharedSessionRepo

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSharedSessionRepo()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SharedSessionsViewModel {
        val scope = CoroutineScope(testDispatcher)
        return SharedSessionsViewModel(
            sharedSessionRepository = fakeRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue(state.sharedSessions.isEmpty())
        assertTrue(state.invitations.isEmpty())
        assertFalse(state.isLoadingSharedSessions)
        assertFalse(state.isLoadingInvitations)
        assertNull(state.error)
    }

    @Test
    fun loadSharedSessionsPopulatesState() = runTest(testDispatcher) {
        fakeRepo.sharedSessions = listOf(
            SharedSession(id = "ss1", name = "Test Session", gameId = "1", ownerId = "1"),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadSharedSessions)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.sharedSessions.size)
        assertEquals("Test Session", state.sharedSessions[0].name)
        assertFalse(state.isLoadingSharedSessions)
    }

    @Test
    fun loadInvitationsPopulatesState() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            SharedSessionInvitation(id = "inv1", sharedSessionId = "ss1", sharedSessionName = "Race Session", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadInvitations)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.invitations.size)
        assertEquals("Race Session", state.invitations[0].sharedSessionName)
        assertFalse(state.isLoadingInvitations)
    }

    @Test
    fun refreshAllLoadsBothSharedSessionsAndInvitations() = runTest(testDispatcher) {
        fakeRepo.sharedSessions = listOf(
            SharedSession(id = "ss1", name = "Session 1", gameId = "1", ownerId = "1"),
        )
        fakeRepo.invitations = listOf(
            SharedSessionInvitation(id = "inv1", sharedSessionId = "ss2", sharedSessionName = "Invite 1", inviterUsername = "bob"),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.RefreshAll)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.sharedSessions.size)
        assertEquals(1, state.invitations.size)
        assertFalse(state.isLoadingSharedSessions)
        assertFalse(state.isLoadingInvitations)
    }

    @Test
    fun createSharedSessionShowsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.CreateSharedSession("New Session", "1", "A fun session"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Shared session created", state.successMessage)
        assertFalse(state.isCreating)
    }

    @Test
    fun createSharedSessionFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.CreateSharedSession("New Session", "1", ""))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isCreating)
    }

    @Test
    fun acceptInvitationShowsSuccessAndRefreshes() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            SharedSessionInvitation(id = "inv1", sharedSessionId = "ss1", sharedSessionName = "Race", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.AcceptInvitation("inv1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Invitation accepted", state.successMessage)
    }

    @Test
    fun rejectInvitationRefreshesInvitations() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            SharedSessionInvitation(id = "inv1", sharedSessionId = "ss1", sharedSessionName = "Race", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadInvitations)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.invitations.size)

        // After rejecting, the repo still returns the same list (in real use it would be updated)
        vm.onIntent(SharedSessionIntent.RejectInvitation("inv1"))
        advanceUntilIdle()

        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadSharedSessions)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(SharedSessionIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissSuccessClearsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.CreateSharedSession("New Session", "1", ""))
        advanceUntilIdle()
        assertNotNull(vm.state.value.successMessage)

        vm.onIntent(SharedSessionIntent.DismissSuccess)
        assertNull(vm.state.value.successMessage)
    }

    @Test
    fun loadSharedSessionsErrorShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadSharedSessions)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingSharedSessions)
    }

    @Test
    fun loadInvitationsErrorShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(SharedSessionIntent.LoadInvitations)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingInvitations)
    }
}

/**
 * Minimal fake shared session repository for unit tests.
 */
class FakeSharedSessionRepo : SharedSessionRepository {
    var sharedSessions: List<SharedSession> = emptyList()
    var sharedSessionDetail: SharedSessionDetail? = null
    var invitations: List<SharedSessionInvitation> = emptyList()
    var sharedSessionSaves: List<SharedSessionSave> = emptyList()
    var shouldFail = false

    override suspend fun getMySharedSessions(): Result<List<SharedSession>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(sharedSessions)
    override suspend fun getSharedSession(sharedSessionId: String): Result<SharedSessionDetail> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else sharedSessionDetail?.let { Result.success(it) } ?: Result.failure(Exception("Not found"))
    override suspend fun getSharedSessionInvitations(): Result<List<SharedSessionInvitation>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(invitations)
    override suspend fun getPendingInvitationCount(): Result<Int> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(invitations.size)
    override suspend fun createSharedSession(name: String, gameId: String, description: String): Result<SharedSessionDetail> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(SharedSessionDetail(id = "new", name = name, gameId = gameId, ownerId = "1"))
    override suspend fun deleteSharedSession(sharedSessionId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun inviteUser(sharedSessionId: String, username: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun acceptInvitation(invitationId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun leaveSharedSession(sharedSessionId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun removeMember(sharedSessionId: String, userId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun getGameSharedSessions(gameId: String): Result<List<SharedSession>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(emptyList())
    override suspend fun getSharedSessionSaves(sharedSessionId: String): Result<List<SharedSessionSave>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(sharedSessionSaves)
    override suspend fun deleteSharedSessionSave(sharedSessionId: String, saveId: Long): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun takeTurn(sharedSessionId: String): Result<String> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success("fake-token")
    override suspend fun releaseTurn(sharedSessionId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun heartbeat(sharedSessionId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun uploadSharedSessionSave(sharedSessionId: String, name: String, turnToken: String, data: ByteArray): Result<SharedSessionSave> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = name))
    override suspend fun downloadSharedSessionSave(sharedSessionId: String, saveId: Long): Result<ByteArray> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(ByteArray(128))
    override suspend fun downloadSharedSessionAutoSave(sharedSessionId: String): Result<ByteArray> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(ByteArray(128))
    override suspend fun uploadSharedSessionAutoSave(sharedSessionId: String, turnToken: String, data: ByteArray): Result<SharedSessionSave> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(SharedSessionSave(id = 1, sharedSessionId = sharedSessionId, name = "Auto Save", isAuto = true))
}
