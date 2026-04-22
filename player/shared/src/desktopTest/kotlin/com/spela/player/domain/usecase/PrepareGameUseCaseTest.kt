package com.spela.player.domain.usecase

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.repository.CorePrunedException
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression coverage for the Phase 2 cache-invalidation branch in
 * PrepareGameUseCase (#555). The pinned-core path is covered elsewhere;
 * these tests pin the "no pin + cached locally" decision matrix:
 *
 *   - isCachedCoreCurrent == null  →  reuse cached path (trust local)
 *   - isCachedCoreCurrent == true  →  reuse cached path
 *   - isCachedCoreCurrent == false →  redownload; update path returned
 *   - cache stale + redownload fails → keep stale path (never strand the user)
 */
class PrepareGameUseCaseTest {

    @Test
    fun reusesCachedCoreWhenStalenessCannotBeDetermined() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = null, // "don't know" branch
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(gameId = "g1").getOrThrow()

        assertEquals("/local/core.so", result.corePath)
        assertEquals(0, core.downloadCoreCalls, "must not redownload when staleness is indeterminate")
    }

    @Test
    fun reusesCachedCoreWhenLocalMatchesServer() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(gameId = "g1").getOrThrow()

        assertEquals("/local/core.so", result.corePath)
        assertEquals(0, core.downloadCoreCalls, "must not redownload when local matches server")
    }

    @Test
    fun redownloadsCachedCoreWhenServerHashDiffers() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false, // stale branch
            downloadResult = Result.success("/local/core-refreshed.so"),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(gameId = "g1").getOrThrow()

        assertEquals("/local/core-refreshed.so", result.corePath)
        assertEquals(1, core.downloadCoreCalls, "stale cache must trigger exactly one redownload")
    }

    @Test
    fun fallsBackToStaleCacheWhenRedownloadFails() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            downloadResult = Result.failure(RuntimeException("network down")),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(gameId = "g1").getOrThrow()

        // User still gets the game; we'd rather run on a stale core than
        // refuse to launch just because the invalidation refresh failed.
        assertEquals("/local/core.so", result.corePath)
        assertNull(result.coreVersionWarning, "stale-fallback path does not surface the pinned-pruned warning")
    }

    @Test
    fun respectsAutoUpdateCoresDisabledEvenWhenServerHasNewerSha() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false, // server thinks local is stale …
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        // … but the user has opted out of auto-updates. We must not
        // call downloadCore; the locally cached binary stays in use.
        val result = useCase.invoke(
            gameId = "g1",
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals("/local/core.so", result.corePath)
        assertEquals(
            0,
            core.downloadCoreCalls,
            "opt-out must short-circuit the staleness check — no redownload even when server has a newer sha",
        )
    }

    // ── #672 core-upgrade decision detection ──────────────────────

    @Test
    fun signalsUpgradeAvailableWhenPinnedShaDiffersAndOptedOut() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32), // 64 chars, differs from pin
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false, // the opt-out case
        ).getOrThrow()

        assertEquals(DecisionKind.UpgradeAvailable, result.decisionKind,
            "pinned sha != server sha + opt-out must surface the decision to the VM")
    }

    @Test
    fun silentUpgradeWhenAutoUpdateEnabledEvenWithMismatchedPin() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = true, // user wants silent upgrades
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "user opted into silent upgrades — the VM must not be asked to show Sheet A")
    }

    @Test
    fun skipsDecisionForSessionsWithNoSaves() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = false, // brand-new session
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "brand-new sessions have no saves at risk — must not prompt")
    }

    @Test
    fun skipsDecisionWhenSessionIsLocked() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            userLockedCoreVersion = true, // user already locked
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "locked sessions must not re-prompt — the lock is the user's decision")
    }

    @Test
    fun skipsDecisionWhenServerShaMatchesPin() = runTest {
        val pinned = "aa".repeat(32)
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
            serverSha = pinned, // server matches pin — no drift
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = pinned,
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "pin matches server — nothing to decide")
    }

    @Test
    fun skipsDecisionWhenServerShaUnavailable() = runTest {
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = null,
            serverSha = null, // network failure / server hasn't fingerprinted
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "can't decide without server sha — fall through to existing behaviour")
    }

    @Test
    fun skipsDecisionWhenSkipFlagIsSet() = runTest {
        // This is the re-entry path: after the user resolves Sheet A
        // the VM re-fires StartGame with skipCoreDecisionPrompt = true.
        // If the detection block didn't honour the flag the sheet
        // would loop forever because the pin is still different from
        // the server sha.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false,
            skipCoreDecisionPrompt = true,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "skipCoreDecisionPrompt must short-circuit the detection block on re-entry")
    }

    // ── Launch-time RehearsalCrashed — sentinel from a prior crash ──

    @Test
    fun signalsRehearsalCrashedWhenSentinelIsSet() = runTest {
        // Previous rehearsal run died before reaching a clean Sheet
        // C/D resolution. Next launch must surface Sheet D BEFORE any
        // other detection — even if the sha hasn't changed and the
        // session isn't locked.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            rehearsalCrashPending = true,
        ).getOrThrow()

        assertEquals(DecisionKind.RehearsalCrashed, result.decisionKind,
            "rehearsalCrashPending=true must surface Sheet D regardless of pin/lock state",
        )
        // VM intercepts before loadCore — corePath isn't used on this
        // branch, so it stays empty.
        assertEquals("", result.corePath)
    }

    @Test
    fun rehearsalCrashedTakesPriorityOverUpgradeAvailable() = runTest {
        // A user who locked + crashed is still locked — but the crash
        // recovery prompt must come first because the user explicitly
        // needs to acknowledge the crash. UpgradeAvailable detection
        // requires `!userLockedCoreVersion`, but RehearsalCrashed must
        // fire even when the lock IS set (different concern entirely).
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false,
            rehearsalCrashPending = true,
        ).getOrThrow()

        assertEquals(DecisionKind.RehearsalCrashed, result.decisionKind,
            "crash recovery must precede upgrade detection — user must acknowledge the crash first",
        )
    }

    @Test
    fun rehearsalCrashedRespectsSkipFlagForResolutionRefires() = runTest {
        // After Sheet D resolution the VM re-fires StartGame with
        // skipCoreDecisionPrompt=true. Detection must short-circuit
        // even though the sentinel is still true on the server (the
        // resolution handlers clear it asynchronously).
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            rehearsalCrashPending = true,
            skipCoreDecisionPrompt = true,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "skipCoreDecisionPrompt must short-circuit RehearsalCrashed on re-entry",
        )
    }

    // ── PinPruned detection — pinned binary rotated out of server retention ──

    @Test
    fun signalsPinPrunedWhenLockedSessionsPinnedBinaryIsGone() = runTest {
        // Server fingerprinted the core but the *specific* pinned
        // historical binary was rotated out. For a user-locked session
        // with saves, the VM must surface Sheet B instead of silently
        // falling through with a toast.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
            // downloadCoreByHash returns CorePrunedException — the pinned
            // binary is no longer in the server's history.
            hashDownloadResult = Result.failure(CorePrunedException("aa".repeat(32))),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            userLockedCoreVersion = true,
        ).getOrThrow()

        assertEquals(DecisionKind.PinPruned, result.decisionKind,
            "user-locked + pruned pin must surface Sheet B, not a silent toast")
        assertEquals("/local/core.so", result.corePath,
            "corePath still resolves to the fallback so Sheet B can preview with a working core")
        assertNull(result.coreVersionWarning,
            "Sheet B replaces the legacy toast — warning must be suppressed when decisionKind is PinPruned")
    }

    @Test
    fun pinPrunedStaysSilentWhenSessionIsNotUserLocked() = runTest {
        // Same pruning event, but the user never explicitly locked — the
        // legacy silent-fallback-with-warning behaviour is preserved so
        // we don't churn the pre-#672 UX for the common unpinned case.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
            hashDownloadResult = Result.failure(CorePrunedException("aa".repeat(32))),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            userLockedCoreVersion = false, // not locked — legacy path
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "non-locked sessions keep the legacy silent-fallback behaviour")
        assertEquals(
            "Original core version no longer available. The latest core may not load this save correctly.",
            result.coreVersionWarning,
            "legacy warning copy must still be shown when decisionKind is None",
        )
    }

    @Test
    fun pinPrunedSkipsWhenSkipFlagIsSet() = runTest {
        // Re-entry after the user resolved Sheet B — the VM re-fires
        // with skipCoreDecisionPrompt = true. Detection must not loop
        // back into PinPruned on the same launch.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = true,
            hashDownloadResult = Result.failure(CorePrunedException("aa".repeat(32))),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            userLockedCoreVersion = true,
            skipCoreDecisionPrompt = true,
        ).getOrThrow()

        assertEquals(DecisionKind.None, result.decisionKind,
            "skipCoreDecisionPrompt must short-circuit PinPruned on re-entry")
    }

    @Test
    fun upgradeAvailableResultCarriesServerCoreSha() = runTest {
        // The VM reads prepared.serverCoreSha to populate Sheet A's
        // 'new version' display without a second network call.
        val core = FakeCoreRepository(
            local = "/local/core.so",
            isCurrent = false,
            serverSha = "bb".repeat(32),
        )
        val useCase = PrepareGameUseCase(FakeDownloadRepository(), core)

        val result = useCase.invoke(
            gameId = "g1",
            pinnedCoreSha256 = "aa".repeat(32),
            sessionHasSaves = true,
            autoUpdateCoresEnabled = false,
        ).getOrThrow()

        assertEquals(DecisionKind.UpgradeAvailable, result.decisionKind)
        assertEquals("bb".repeat(32), result.serverCoreSha,
            "serverCoreSha must be surfaced on the result so the VM doesn't re-fetch")
    }
}

private class FakeDownloadRepository : DownloadRepository {
    override fun observeDownloads(): Flow<List<DownloadProgress>> = emptyFlow()
    override fun observeDownload(gameId: String): Flow<DownloadProgress> = emptyFlow()
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = emptyFlow()
    override suspend fun downloadGame(gameId: String, gameTitle: String) = Result.success("/local/game.rom")
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String = "/local/game.rom"
    override suspend fun isGameCached(gameId: String) = true
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize() = 0L
    override suspend fun clearCache() {}
}

/**
 * Minimal CoreRepository fake configured by the test. Always returns
 * "nestopia" from getRecommendedCore so PrepareGameUseCase reaches the
 * unpinned branch; the core name is not substituted on the desktop
 * (currentPlatform() == "desktop" / "linux" / "windows" / "macos" and
 * only macOS has substitutions, and nestopia isn't in that map).
 */
private class FakeCoreRepository(
    private val local: String?,
    private val isCurrent: Boolean?,
    private val downloadResult: Result<String> = Result.success("/local/downloaded-core.so"),
    private val serverSha: String? = null,
    // PinPruned tests override this with Result.failure(CorePrunedException(...)).
    // Default stays on the old "not exercised here" marker for the pre-3c tests.
    private val hashDownloadResult: Result<String> = Result.failure(
        UnsupportedOperationException("pinned path not exercised here"),
    ),
) : CoreRepository {
    var downloadCoreCalls = 0
        private set

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> =
        Result.success(listOf(LibretroCore(id = 1L, name = "nestopia")))

    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> =
        Result.success(LibretroCore(id = 1L, name = "nestopia"))

    override suspend fun downloadCore(
        coreName: String,
        downloadUrl: String?,
        onProgress: (Float) -> Unit,
    ): Result<String> {
        downloadCoreCalls++
        return downloadResult
    }

    override suspend fun downloadCoreByHash(
        coreName: String,
        sha256: String,
        onProgress: (Float) -> Unit,
    ): Result<String> = hashDownloadResult

    override suspend fun getLocalCorePath(coreName: String): String? = local

    override suspend fun isCoreCached(coreName: String): Boolean = local != null

    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? = isCurrent
    override suspend fun getServerCoreSha(coreName: String): String? = serverSha
}
