package com.spela.player.domain.usecase

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.LibretroCore
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
    ): Result<String> = Result.failure(UnsupportedOperationException("pinned path not exercised here"))

    override suspend fun getLocalCorePath(coreName: String): String? = local

    override suspend fun isCoreCached(coreName: String): Boolean = local != null

    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? = isCurrent
    override suspend fun getServerCoreSha(coreName: String): String? = serverSha
}
