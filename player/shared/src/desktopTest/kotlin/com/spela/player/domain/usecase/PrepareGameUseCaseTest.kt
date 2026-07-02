package com.spela.player.domain.usecase

import com.spela.player.data.repository.CoreUpdateService
import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadState
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.INSTANT_DOWNLOAD_FALLBACK_DELAY_MS
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.CorePrunedException
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.PreferencesRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Constructs a [PrepareGameUseCase] backed by the supplied [core] plus a
 * lightweight [CoreUpdateService] wired to the test scope. The service
 * isn't exercised here — these tests pin the use case's own decision
 * branches, and CoreUpdateService is covered separately in
 * CoreUpdateServiceTest (#1192). The presence of the service is purely
 * to satisfy the constructor.
 */
private fun TestScope.buildPrepareGameUseCase(
    core: CoreRepository,
    downloads: DownloadRepository = FakeDownloadRepository(),
): PrepareGameUseCase {
    val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
    }
    val updateService = CoreUpdateService(
        coreRepository = core,
        preferencesRepository = StubPreferencesRepository(),
        dispatchers = dispatchers,
        scope = this.backgroundScope,
    )
    return PrepareGameUseCase(downloads, core, updateService)
}

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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
        val useCase = buildPrepareGameUseCase(core)

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

    // ── #1412 on-demand ROM download gate ─────────────────────────
    // Only the game-detail Play button used to gate uncached games into
    // the download-then-play flow; every other launch entry point reached
    // PrepareGameUseCase directly and failed hard with "Game not downloaded".
    // The use case now downloads the ROM on demand so any entry point can
    // launch an un-downloaded game.

    @Test
    fun downloadsRomOnDemandWhenMissingThenLaunches() = runTest {
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(
            initialPath = null, // ROM not on disk at launch …
            pathAfterDownload = "/local/games/g1/game.nes", // … present after download
        )
        val useCase = buildPrepareGameUseCase(core, downloads)

        val result = useCase.invoke(gameId = "g1", gameTitle = "Super Mario Bros.").getOrThrow()

        assertEquals("/local/games/g1/game.nes", result.gamePath)
        assertEquals(1, downloads.downloadGameCalls, "a missing ROM must be downloaded exactly once")
        assertEquals(
            "Super Mario Bros.",
            downloads.lastDownloadGameTitle,
            "the game title must be forwarded so the download sheet isn't a generic 'game'",
        )
    }

    @Test
    fun doesNotDownloadWhenRomAlreadyPresent() = runTest {
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(initialPath = "/local/games/g1/game.nes")
        val useCase = buildPrepareGameUseCase(core, downloads)

        val result = useCase.invoke(gameId = "g1").getOrThrow()

        assertEquals("/local/games/g1/game.nes", result.gamePath)
        assertEquals(0, downloads.downloadGameCalls, "an already-cached ROM must not be re-downloaded")
    }

    @Test
    fun failsWhenRomDownloadFails() = runTest {
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(
            initialPath = null,
            downloadResult = Result.failure(RuntimeException("network down")),
        )
        val useCase = buildPrepareGameUseCase(core, downloads)

        val result = useCase.invoke(gameId = "g1")

        assertTrue(result.isFailure, "a failed ROM download must fail prepare, not launch a missing ROM")
        assertEquals(1, downloads.downloadGameCalls)
    }

    @Test
    fun suppressesProgressSheetForFastRomDownloads() = runTest {
        // A 32 KB ROM finishes well within the silent window, so no progress
        // sheet should ever be surfaced — only the terminal null. (#1412 / #932)
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(
            initialPath = null,
            pathAfterDownload = "/local/games/g1/game.nes",
            downloadDelayMs = INSTANT_DOWNLOAD_FALLBACK_DELAY_MS / 2, // finishes before the window
            progress = DownloadProgress("g1", "Game", DownloadState.DOWNLOADING, 16_000, 32_000),
        )
        val surfaced = mutableListOf<DownloadProgress?>()
        val useCase = buildPrepareGameUseCase(core, downloads)

        useCase.invoke(gameId = "g1", onGameDownload = { surfaced.add(it) }).getOrThrow()

        assertTrue(
            surfaced.none { it != null },
            "a fast download must not flash the progress sheet (surfaced=$surfaced)",
        )
    }

    @Test
    fun surfacesProgressSheetForSlowRomDownloads() = runTest {
        // A download still running past the silent window surfaces progress so
        // the user isn't staring at a frozen screen. (#1412)
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(
            initialPath = null,
            pathAfterDownload = "/local/games/g1/game.iso",
            downloadDelayMs = INSTANT_DOWNLOAD_FALLBACK_DELAY_MS * 3, // outlasts the window
            progress = DownloadProgress("g1", "Game", DownloadState.DOWNLOADING, 50_000, 9_000_000),
        )
        val surfaced = mutableListOf<DownloadProgress?>()
        val useCase = buildPrepareGameUseCase(core, downloads)

        useCase.invoke(gameId = "g1", onGameDownload = { surfaced.add(it) }).getOrThrow()

        assertTrue(
            surfaced.any { it != null },
            "a slow download must surface progress after the silent window (surfaced=$surfaced)",
        )
    }

    @Test
    fun failsWhenRomStillMissingAfterDownload() = runTest {
        val core = FakeCoreRepository(local = "/local/core.so", isCurrent = true)
        val downloads = ConfigurableDownloadRepository(
            initialPath = null,
            pathAfterDownload = null, // download reports success but path still won't resolve
        )
        val useCase = buildPrepareGameUseCase(core, downloads)

        val result = useCase.invoke(gameId = "g1")

        assertTrue(result.isFailure, "if the ROM still isn't resolvable after download, prepare must fail")
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
    override suspend fun scanForOrphanedDownloads() {}
}

/**
 * Download fake for the #1412 on-demand-ROM tests. [getLocalGamePath]
 * starts at [initialPath]; a successful [downloadGame] flips it to
 * [pathAfterDownload] and bumps [downloadGameCalls] so tests can assert
 * whether (and how often) the ROM was fetched.
 */
private class ConfigurableDownloadRepository(
    initialPath: String?,
    private val pathAfterDownload: String? = null,
    private val downloadResult: Result<String> = Result.success("/local/game.rom"),
    /** Virtual-time duration the download "takes" — drives the #1412 silent-window tests. */
    private val downloadDelayMs: Long = 0,
    /** Progress value [observeDownload] emits while the download is in flight. */
    private val progress: DownloadProgress? = null,
) : DownloadRepository {
    var downloadGameCalls = 0
        private set
    var lastDownloadGameTitle: String? = null
        private set

    private var currentPath: String? = initialPath

    override fun observeDownloads(): Flow<List<DownloadProgress>> = emptyFlow()
    override fun observeDownload(gameId: String): Flow<DownloadProgress> =
        if (progress != null) flowOf(progress) else emptyFlow()
    override fun observeDownloadedGames(): Flow<List<DownloadedGame>> = emptyFlow()
    override suspend fun downloadGame(gameId: String, gameTitle: String): Result<String> {
        downloadGameCalls++
        lastDownloadGameTitle = gameTitle
        if (downloadDelayMs > 0) delay(downloadDelayMs)
        if (downloadResult.isSuccess) currentPath = pathAfterDownload
        return downloadResult
    }
    override suspend fun cancelDownload(gameId: String) {}
    override suspend fun getLocalGamePath(gameId: String): String? = currentPath
    override suspend fun isGameCached(gameId: String) = currentPath != null
    override suspend fun deleteLocalGame(gameId: String) {}
    override suspend fun getCacheSize() = 0L
    override suspend fun clearCache() {}
    override suspend fun scanForOrphanedDownloads() {}
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
        customDownloadUrl: String?,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ): Result<String> {
        downloadCoreCalls++
        return downloadResult
    }

    override suspend fun downloadCoreByHash(
        coreName: String,
        sha256: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = hashDownloadResult

    override suspend fun getLocalCorePath(coreName: String): String? = local

    override suspend fun isCoreCached(coreName: String): Boolean = local != null

    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? = isCurrent
    override suspend fun getServerCoreSha(coreName: String): String? = serverSha
}

/**
 * Minimum-viable [PreferencesRepository] for tests that only need
 * [getPreferences] (used by [CoreUpdateService] to gate its prefetch
 * pass). Every other method throws so an accidental dependency in a
 * future test surfaces loudly rather than returning a silent default.
 */
private class StubPreferencesRepository : PreferencesRepository {
    override suspend fun getPreferences(): Result<UserPreferences> =
        Result.success(UserPreferences())

    override suspend fun updatePreferences(
        showPerformanceOverlay: Boolean?,
        autoSaveEnabled: Boolean?,
        autoLoadSaveEnabled: Boolean?,
        autoUpdateCoresEnabled: Boolean?,
        selectedShader: String?,
        selectedTheme: String?,
        consoleShaders: Map<String, String>?,
        consoleRenderScales: Map<String, String>?,
        consoleSaveStatePolicies: Map<String, String>?,
        gameSaveStatePolicies: Map<String, String>?,
        defaultSecondScreenPage: String?,
    ): Result<UserPreferences> = error("unused")

    override fun getDeviceShaderOverride(consoleId: String): ShaderPreset? = null
    override fun setDeviceShaderOverride(consoleId: String, shader: ShaderPreset?) = Unit
    override fun getAllDeviceShaderOverrides(): Map<String, ShaderPreset> = emptyMap()
    override suspend fun syncDeviceShaderOverrides() = Unit
    override suspend fun resolveShader(consoleId: String): ShaderPreset = ShaderPreset.NONE
    override suspend fun pushDeviceShaderOverridesToServer() = Unit
    override suspend fun syncKeyMappingsFromServer() = Unit
    override suspend fun pushKeyMappingsToServer() = Unit
    override suspend fun pushGameKeyMappingToServer(gameId: String, bindings: Map<Int, Int>) = Unit
    override suspend fun deleteGameKeyMappingOnServer(gameId: String) = Unit
    override suspend fun syncGameKeyMappingFromServer(gameId: String) = Unit
    override fun getOrientationLock(): String = "auto"
    override fun setOrientationLock(mode: String) = Unit
    override fun getControlTab(consoleId: String): String = "gamepad"
    override fun setControlTab(consoleId: String, tab: String) = Unit
    override fun getConsoleListGrouping(): String = "generation"
    override fun setConsoleListGrouping(grouping: String) = Unit
    override fun getConfirmButtonConvention(): String = "xbox"
    override fun setConfirmButtonConvention(convention: String) = Unit
}
