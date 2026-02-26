package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.Relay
import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayInvitation
import com.spela.player.domain.model.RelaySave
import com.spela.player.domain.repository.RelayRepository
import com.spela.player.presentation.intent.RelayIntent
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
class RelaysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepo: FakeRelayRepo

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeRelayRepo()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RelaysViewModel {
        val scope = CoroutineScope(testDispatcher)
        return RelaysViewModel(
            relayRepository = fakeRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertTrue(state.relays.isEmpty())
        assertTrue(state.invitations.isEmpty())
        assertFalse(state.isLoadingRelays)
        assertFalse(state.isLoadingInvitations)
        assertNull(state.error)
    }

    @Test
    fun loadRelaysPopulatesState() = runTest(testDispatcher) {
        fakeRepo.relays = listOf(
            Relay(id = "r1", name = "Test Relay", gameId = "1", ownerId = "1"),
        )
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadRelays)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.relays.size)
        assertEquals("Test Relay", state.relays[0].name)
        assertFalse(state.isLoadingRelays)
    }

    @Test
    fun loadInvitationsPopulatesState() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            RelayInvitation(id = "inv1", relayId = "r1", relayName = "Race Relay", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadInvitations)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.invitations.size)
        assertEquals("Race Relay", state.invitations[0].relayName)
        assertFalse(state.isLoadingInvitations)
    }

    @Test
    fun refreshAllLoadsBothRelaysAndInvitations() = runTest(testDispatcher) {
        fakeRepo.relays = listOf(
            Relay(id = "r1", name = "Relay 1", gameId = "1", ownerId = "1"),
        )
        fakeRepo.invitations = listOf(
            RelayInvitation(id = "inv1", relayId = "r2", relayName = "Invite 1", inviterUsername = "bob"),
        )
        val vm = createViewModel()
        vm.onIntent(RelayIntent.RefreshAll)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.relays.size)
        assertEquals(1, state.invitations.size)
        assertFalse(state.isLoadingRelays)
        assertFalse(state.isLoadingInvitations)
    }

    @Test
    fun createRelayShowsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(RelayIntent.CreateRelay("New Relay", "1", "A fun relay"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Relay created", state.successMessage)
        assertFalse(state.isCreating)
    }

    @Test
    fun createRelayFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayIntent.CreateRelay("New Relay", "1", ""))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isCreating)
    }

    @Test
    fun acceptInvitationShowsSuccessAndRefreshes() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            RelayInvitation(id = "inv1", relayId = "r1", relayName = "Race", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(RelayIntent.AcceptInvitation("inv1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Invitation accepted", state.successMessage)
    }

    @Test
    fun rejectInvitationRefreshesInvitations() = runTest(testDispatcher) {
        fakeRepo.invitations = listOf(
            RelayInvitation(id = "inv1", relayId = "r1", relayName = "Race", inviterUsername = "alice"),
        )
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadInvitations)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.invitations.size)

        // After rejecting, the repo still returns the same list (in real use it would be updated)
        vm.onIntent(RelayIntent.RejectInvitation("inv1"))
        advanceUntilIdle()

        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadRelays)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(RelayIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissSuccessClearsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(RelayIntent.CreateRelay("New Relay", "1", ""))
        advanceUntilIdle()
        assertNotNull(vm.state.value.successMessage)

        vm.onIntent(RelayIntent.DismissSuccess)
        assertNull(vm.state.value.successMessage)
    }

    @Test
    fun loadRelaysErrorShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadRelays)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingRelays)
    }

    @Test
    fun loadInvitationsErrorShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayIntent.LoadInvitations)
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingInvitations)
    }
}

/**
 * Minimal fake relay repository for unit tests.
 */
class FakeRelayRepo : RelayRepository {
    var relays: List<Relay> = emptyList()
    var relayDetail: RelayDetail? = null
    var invitations: List<RelayInvitation> = emptyList()
    var relaySaves: List<RelaySave> = emptyList()
    var shouldFail = false

    override suspend fun getMyRelays(page: Int, pageSize: Int): Result<List<Relay>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(relays)
    override suspend fun getRelay(relayId: String): Result<RelayDetail> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else relayDetail?.let { Result.success(it) } ?: Result.failure(Exception("Not found"))
    override suspend fun getRelayInvitations(): Result<List<RelayInvitation>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(invitations)
    override suspend fun getPendingInvitationCount(): Result<Int> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(invitations.size)
    override suspend fun createRelay(name: String, gameId: String, description: String): Result<RelayDetail> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(RelayDetail(id = "new", name = name, gameId = gameId, ownerId = "1"))
    override suspend fun deleteRelay(relayId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun inviteUser(relayId: String, username: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun acceptInvitation(invitationId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun rejectInvitation(invitationId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun leaveRelay(relayId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun removeMember(relayId: String, userId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun getGameRelays(gameId: String): Result<List<Relay>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(emptyList())
    override suspend fun getRelaySaves(relayId: String): Result<List<RelaySave>> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(relaySaves)
    override suspend fun deleteRelaySave(relayId: String, saveId: Long): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun takeTurn(relayId: String): Result<String> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success("fake-token")
    override suspend fun releaseTurn(relayId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun heartbeat(relayId: String): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
    override suspend fun uploadRelaySave(relayId: String, name: String, turnToken: String, data: ByteArray): Result<RelaySave> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(RelaySave(id = 1, relayId = relayId, name = name, fileSize = data.size.toLong()))
    override suspend fun downloadRelaySave(relayId: String, saveId: Long): Result<ByteArray> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(ByteArray(128))
    override suspend fun downloadRelayAutoSave(relayId: String): Result<ByteArray> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(ByteArray(128))
    override suspend fun uploadRelayAutoSave(relayId: String, turnToken: String, data: ByteArray): Result<RelaySave> =
        if (shouldFail) Result.failure(Exception("Network error"))
        else Result.success(RelaySave(id = 1, relayId = relayId, name = "Auto Save", isAuto = true, fileSize = data.size.toLong()))
    override suspend fun copyRelaySaveToGame(relayId: String, saveId: Long): Result<Unit> =
        if (shouldFail) Result.failure(Exception("Network error")) else Result.success(Unit)
}
