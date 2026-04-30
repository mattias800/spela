package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.GameSession
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.SessionCheatConfig
import com.spela.player.domain.repository.SessionRepository
import com.spela.player.presentation.intent.SessionDetailIntent
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the #672 UnlockCoreVersion intent wired up on the session
 * detail screen. The lock chip's "Use the latest version instead" link
 * dispatches this intent; the VM must clear `userLockedCoreVersion` on
 * the server so the next launch re-enters the upgrade decision flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepo: FakeSessionRepo

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSessionRepo()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SessionDetailViewModel {
        val scope = CoroutineScope(testDispatcher)
        return SessionDetailViewModel(
            sessionRepository = fakeRepo,
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    @Test
    fun unlockCoreVersionClearsFlagOnServerAndUpdatesStateOnSuccess() = runTest(testDispatcher) {
        fakeRepo.sessions += GameSession(
            id = "s1",
            gameId = "g1",
            name = "Run 1",
            pinnedCoreSha256 = "aa".repeat(32),
            userLockedCoreVersion = true,
        )
        val vm = createViewModel()

        vm.onIntent(SessionDetailIntent.UnlockCoreVersion("s1"))
        advanceUntilIdle()

        assertEquals(1, fakeRepo.updateCoreFlagsCalls.size,
            "unlock must call updateSessionCoreFlags exactly once")
        val call = fakeRepo.updateCoreFlagsCalls.single()
        assertEquals("s1", call.sessionId)
        assertEquals(false, call.userLockedCoreVersion,
            "unlock must push userLockedCoreVersion=false so the server clears the flag")
        assertNull(call.autoLoadSuppressed,
            "unlock must not touch autoLoadSuppressed — that's Sheet B / D's job")
        assertNull(call.rehearsalCrashPending,
            "unlock must not touch rehearsalCrashPending — rehearsal is a separate flow")

        val state = vm.state.value
        val session = state.session
        assertNotNull(session, "session must be present after the server returns the updated row")
        assertFalse(session.userLockedCoreVersion,
            "state's session must reflect the cleared lock")
        assertEquals(
            "Lock cleared. We'll check for a newer core next time you play.",
            state.successMessage,
            "success toast must match the spec copy so users see why the chip disappeared",
        )
        assertNull(state.error)
    }

    @Test
    fun unlockCoreVersionRevertsOptimisticUpdateOnFailure() = runTest(testDispatcher) {
        // Seed a session + LoadSession so the VM has a "previous"
        // session to revert to — this test exercises the revert path
        // on the optimistic-update happy/sad split.
        fakeRepo.sessions += GameSession(
            id = "s1",
            gameId = "g1",
            name = "Run 1",
            pinnedCoreSha256 = "aa".repeat(32),
            userLockedCoreVersion = true,
        )
        fakeRepo.failUpdateCoreFlagsWith = RuntimeException("network down")
        val vm = createViewModel()
        vm.onIntent(SessionDetailIntent.LoadSession("s1"))
        advanceUntilIdle()

        vm.onIntent(SessionDetailIntent.UnlockCoreVersion("s1"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("network down", state.error,
            "failure must surface as a dismissable error so the user can retry")
        val session = state.session
        assertNotNull(session, "revert path must restore the session we optimistically flipped")
        assertTrue(session.userLockedCoreVersion,
            "failure must revert the optimistic unlock — the chip reappears so the user knows the action didn't stick",
        )
        assertNull(state.successMessage)
    }

    @Test
    fun unlockingAnUnknownSessionReturnsErrorInsteadOfCrashing() = runTest(testDispatcher) {
        // Rare race: user taps the link right as the session is being
        // deleted on another device. The fake's "session not found"
        // failure mirrors what the real server returns (404).
        val vm = createViewModel()

        vm.onIntent(SessionDetailIntent.UnlockCoreVersion("does-not-exist"))
        advanceUntilIdle()

        assertTrue(vm.state.value.error.orEmpty().isNotEmpty(),
            "missing session must surface a user-visible error, not silently swallow")
    }
}

/**
 * Minimal SessionRepository stub scoped to what SessionDetailViewModelTest
 * exercises. Records every call to [updateSessionCoreFlags] so the test
 * can assert the exact flag payload that reached the server.
 */
private class FakeSessionRepo : SessionRepository {
    val sessions: MutableList<GameSession> = mutableListOf()

    data class UpdateCoreFlagsInvocation(
        val sessionId: String,
        val userLockedCoreVersion: Boolean?,
        val autoLoadSuppressed: Boolean?,
        val rehearsalCrashPending: Boolean?,
    )
    val updateCoreFlagsCalls: MutableList<UpdateCoreFlagsInvocation> = mutableListOf()

    /** Test hook: when set, [updateSessionCoreFlags] fails with this error. */
    var failUpdateCoreFlagsWith: Throwable? = null

    override suspend fun getSessionsForGame(gameId: String): Result<List<GameSession>> =
        Result.success(sessions.filter { it.gameId == gameId })

    override suspend fun getSession(sessionId: String): Result<GameSession> {
        val s = sessions.find { it.id == sessionId }
            ?: return Result.failure(Exception("Session not found"))
        return Result.success(s)
    }

    override suspend fun createSession(gameId: String, name: String): Result<GameSession> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun createSessionFromSharedSave(gameId: String, saveId: String): Result<GameSession> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun updateSession(sessionId: String, name: String?, coreName: String?): Result<GameSession> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun updateSessionCoreFlags(
        sessionId: String,
        userLockedCoreVersion: Boolean?,
        autoLoadSuppressed: Boolean?,
        rehearsalCrashPending: Boolean?,
    ): Result<GameSession> {
        updateCoreFlagsCalls += UpdateCoreFlagsInvocation(
            sessionId = sessionId,
            userLockedCoreVersion = userLockedCoreVersion,
            autoLoadSuppressed = autoLoadSuppressed,
            rehearsalCrashPending = rehearsalCrashPending,
        )
        failUpdateCoreFlagsWith?.let { return Result.failure(it) }
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx < 0) return Result.failure(Exception("Session not found"))
        var updated = sessions[idx]
        if (userLockedCoreVersion != null) updated = updated.copy(userLockedCoreVersion = userLockedCoreVersion)
        if (autoLoadSuppressed != null) updated = updated.copy(autoLoadSuppressed = autoLoadSuppressed)
        if (rehearsalCrashPending != null) updated = updated.copy(rehearsalCrashPending = rehearsalCrashPending)
        sessions[idx] = updated
        return Result.success(sessions[idx])
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun getSessionSaves(sessionId: String): Result<List<SaveState>> =
        Result.success(emptyList())

    override suspend fun updateSessionSave(
        sessionId: String,
        saveId: String,
        name: String,
    ): Result<SaveState> = Result.failure(UnsupportedOperationException())

    override suspend fun deleteSessionSave(
        sessionId: String,
        saveId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun uploadSessionSave(
        sessionId: String,
        name: String,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<SaveState> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSessionSaveFromFile(
        sessionId: String,
        name: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<SaveState> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSessionSave(sessionId: String, saveId: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSessionAutoSave(
        sessionId: String,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSessionAutoSaveFromFile(
        sessionId: String,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSessionAutoSave(sessionId: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSessionAutoSaveToFile(sessionId: String, outputPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSlotSave(
        sessionId: String,
        slot: Int,
        data: ByteArray,
        screenshot: ByteArray?,
        coreName: String,
    ): Result<SaveState> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSlotSaveFromFile(
        sessionId: String,
        slot: Int,
        savePath: String,
        saveSize: Long,
        screenshot: ByteArray?,
        coreName: String,
        compression: String,
    ): Result<SaveState> = Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSlotSave(sessionId: String, slot: Int): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSlotSaveToFile(sessionId: String, slot: Int, outputPath: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun uploadSessionSram(sessionId: String, data: ByteArray, coreName: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun downloadSessionSram(sessionId: String): Result<ByteArray> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun getSessionCheats(sessionId: String): Result<SessionCheatConfig> =
        Result.success(SessionCheatConfig(cheatsEnabled = false, enabledIndices = emptyList()))

    override suspend fun updateSessionCheats(
        sessionId: String,
        cheatsEnabled: Boolean,
        enabledIndices: List<Int>,
    ): Result<SessionCheatConfig> =
        Result.failure(UnsupportedOperationException("not exercised"))

    override suspend fun cloneSession(sessionId: String, name: String?, saveId: Long?): Result<GameSession> =
        Result.failure(UnsupportedOperationException("not exercised"))
}
