package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.RelayDetail
import com.spela.player.domain.model.RelayMember
import com.spela.player.domain.model.RelaySave
import com.spela.player.presentation.intent.RelayDetailIntent
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
class RelayDetailViewModelTest {

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

    private fun createViewModel(): RelayDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        return RelayDetailViewModel(
            relayRepository = fakeRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createViewModel()
        val state = vm.state.value
        assertNull(state.relay)
        assertTrue(state.saves.isEmpty())
        assertFalse(state.isLoadingRelay)
        assertFalse(state.isLoadingSaves)
        assertNull(state.turnToken)
    }

    @Test
    fun loadRelayPopulatesState() = runTest(testDispatcher) {
        fakeRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            gameTitle = "Castlevania",
            ownerId = "1",
            ownerUsername = "player",
            memberCount = 2,
            members = listOf(
                RelayMember(userId = "1", username = "player", role = "owner"),
                RelayMember(userId = "2", username = "alice", role = "member"),
            ),
        )
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LoadRelay("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.relay)
        assertEquals("Test Relay", state.relay!!.name)
        assertEquals(2, state.relay!!.members.size)
        assertFalse(state.isLoadingRelay)
    }

    @Test
    fun loadRelayFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LoadRelay("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.relay)
        assertNotNull(state.error)
        assertFalse(state.isLoadingRelay)
    }

    @Test
    fun loadSavesPopulatesState() = runTest(testDispatcher) {
        fakeRepo.relaySaves = listOf(
            RelaySave(id = 1, relayId = "r1", username = "player", name = "Save 1", fileSize = 4096),
            RelaySave(id = 2, relayId = "r1", username = "alice", name = "Auto Save", isAuto = true, fileSize = 4096),
        )
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LoadSaves("r1"))
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
        vm.onIntent(RelayDetailIntent.LoadSaves("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingSaves)
    }

    @Test
    fun takeTurnStoresTurnToken() = runTest(testDispatcher) {
        fakeRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.TakeTurn("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("fake-token", state.turnToken)
        assertFalse(state.isTakingTurn)
    }

    @Test
    fun takeTurnFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.TakeTurn("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.turnToken)
        assertNotNull(state.error)
        assertFalse(state.isTakingTurn)
    }

    @Test
    fun releaseTurnClearsTurnToken() = runTest(testDispatcher) {
        fakeRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()

        // Take turn first
        vm.onIntent(RelayDetailIntent.TakeTurn("r1"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.turnToken)

        // Release turn
        vm.onIntent(RelayDetailIntent.ReleaseTurn("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.turnToken)
        assertFalse(state.isReleasingTurn)
    }

    @Test
    fun releaseTurnFailureShowsError() = runTest(testDispatcher) {
        val vm = createViewModel()
        // Take turn successfully first
        vm.onIntent(RelayDetailIntent.TakeTurn("r1"))
        advanceUntilIdle()

        // Now make release fail
        fakeRepo.shouldFail = true
        vm.onIntent(RelayDetailIntent.ReleaseTurn("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isReleasingTurn)
    }

    @Test
    fun inviteUserShowsSuccessMessage() = runTest(testDispatcher) {
        fakeRepo.relayDetail = RelayDetail(
            id = "r1",
            name = "Test Relay",
            gameId = "1",
            ownerId = "1",
        )
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.InviteUser("r1", "bob"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Invitation sent to bob", state.successMessage)
        assertFalse(state.isInviting)
    }

    @Test
    fun inviteUserFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.InviteUser("r1", "bob"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
        assertFalse(state.isInviting)
    }

    @Test
    fun leaveRelayShowsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LeaveRelay("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("You left the relay", state.successMessage)
    }

    @Test
    fun leaveRelayFailureShowsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LeaveRelay("r1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertNotNull(state.error)
    }

    @Test
    fun dismissErrorClearsError() = runTest(testDispatcher) {
        fakeRepo.shouldFail = true
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.LoadRelay("r1"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)

        vm.onIntent(RelayDetailIntent.DismissError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun dismissSuccessClearsSuccessMessage() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onIntent(RelayDetailIntent.InviteUser("r1", "bob"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.successMessage)

        vm.onIntent(RelayDetailIntent.DismissSuccess)
        assertNull(vm.state.value.successMessage)
    }
}
