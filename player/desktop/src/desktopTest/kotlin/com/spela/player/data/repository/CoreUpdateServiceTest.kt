package com.spela.player.data.repository

import com.spela.player.desktop.e2e.FakePreferencesRepository
import com.spela.player.domain.model.LibretroCore
import com.spela.player.domain.model.UserPreferences
import com.spela.player.domain.repository.CoreRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [CoreUpdateService]. Lives in desktopTest (rather than
 * commonTest) so it can reuse the rich [FakePreferencesRepository] from
 * the existing e2e fakes module — implementing the full
 * `PreferencesRepository` surface inline for one test would be more
 * noise than signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreUpdateServiceTest {

    private val testDispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(testDispatcher)
    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var coreRepo: SettableCoreRepository
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var service: CoreUpdateService

    @BeforeTest
    fun setUp() {
        coreRepo = SettableCoreRepository()
        preferences = FakePreferencesRepository()
        service = CoreUpdateService(coreRepo, preferences, dispatchers, scope)
    }

    @Test
    fun prefetchOnlyTouchesCachedStaleCores() = runTest(testDispatcher) {
        coreRepo.cores = listOf(
            core("nestopia"),
            core("snes9x"),
            core("mgba"),
            core("dolphin"),
        )
        // nestopia is cached and stale → should be downloaded.
        // snes9x is cached and current → skipped.
        // mgba is cached, server can't decide (null) → skipped per
        //   tri-state semantics ("can't decide" defers to per-launch check).
        // dolphin is not cached at all → skipped.
        coreRepo.cachedCores = setOf("nestopia", "snes9x", "mgba")
        coreRepo.currentByName["nestopia"] = false
        coreRepo.currentByName["snes9x"] = true
        coreRepo.currentByName["mgba"] = null

        service.prefetchStaleCachedCores()
        advanceUntilIdle()

        assertEquals(listOf("nestopia"), coreRepo.downloadCalls,
            "only the cached AND explicitly-stale core gets prefetched")
    }

    @Test
    fun prefetchNoopsWhenAutoUpdateDisabled() = runTest(testDispatcher) {
        preferences.preferencesResult = Result.success(
            UserPreferences(autoUpdateCoresEnabled = false),
        )
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.cachedCores = setOf("nestopia")
        coreRepo.currentByName["nestopia"] = false

        service.prefetchStaleCachedCores()
        advanceUntilIdle()

        assertTrue(coreRepo.downloadCalls.isEmpty(),
            "auto-update preference off ⇒ no downloads")
    }

    @Test
    fun prefetchIsSingleFlightAcrossCalls() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.cachedCores = setOf("nestopia")
        coreRepo.currentByName["nestopia"] = false

        service.prefetchStaleCachedCores()
        service.prefetchStaleCachedCores()
        service.prefetchStaleCachedCores()
        advanceUntilIdle()

        assertEquals(1, coreRepo.downloadCalls.size,
            "second/third calls within a session must no-op")
    }

    @Test
    fun downloadCoreDeduplicatesConcurrentCallers() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.gateDownload = true

        val first = service.downloadCore("nestopia", null)
        val second = service.downloadCore("nestopia", null)
        assertTrue(first === second, "concurrent callers must share one handle")

        coreRepo.releaseDownload()
        advanceUntilIdle()
        assertEquals(1, coreRepo.downloadCalls.size)
    }

    @Test
    fun inFlightDownloadIsClearedAfterCompletion() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))

        val handle = service.downloadCore("nestopia", null)
        advanceUntilIdle()
        assertEquals("/fake/nestopia", handle.await().getOrNull())

        assertNull(service.inFlightDownload("nestopia"),
            "handle must be removed from the registry post-completion")
    }

    @Test
    fun progressByteCountersAreSurfacedThroughHandle() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.progressEmissions = listOf(0L to 100L, 50L to 100L, 100L to 100L)

        val handle = service.downloadCore("nestopia", null)
        advanceUntilIdle()

        val snapshot = handle.progress.value
        assertEquals(100L, snapshot.bytesDownloaded)
        assertEquals(100L, snapshot.totalBytes)
    }

    @Test
    fun completionSnapshotFlipsToTotalWhenTotalWasKnown() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.progressEmissions = listOf(50L to 200L)

        val handle = service.downloadCore("nestopia", null)
        advanceUntilIdle()

        val snapshot = handle.progress.value
        assertEquals(200L, snapshot.totalBytes)
        assertEquals(200L, snapshot.bytesDownloaded)
    }

    @Test
    fun prefetchSkipsNeverCachedCoresEvenIfStaleAtServer() = runTest(testDispatcher) {
        // Bandwidth guardrail: never download a core the user has not
        // played. Stale-at-server only matters if there's already a
        // local copy that's wrong.
        coreRepo.cores = listOf(core("opera"))
        coreRepo.cachedCores = emptySet()
        coreRepo.currentByName["opera"] = false

        service.prefetchStaleCachedCores()
        advanceUntilIdle()

        assertTrue(coreRepo.downloadCalls.isEmpty())
    }

    @Test
    fun handleReturnedFromInFlightDownloadIsTheSameAsActiveOne() = runTest(testDispatcher) {
        coreRepo.cores = listOf(core("nestopia"))
        coreRepo.gateDownload = true

        val created = service.downloadCore("nestopia", null)
        val recovered = service.inFlightDownload("nestopia")
        assertNotNull(recovered)
        assertTrue(created === recovered,
            "inFlightDownload must hand back the live registry entry, " +
                "not a fresh handle")

        coreRepo.releaseDownload()
        advanceUntilIdle()
    }

    private fun core(name: String) = LibretroCore(
        id = name.hashCode().toLong(),
        name = name,
        displayName = name.replaceFirstChar { it.uppercase() },
    )
}

/**
 * Test-only [CoreRepository] with knobs for cache state, staleness, and
 * download gating. The desktop e2e module's [FakeCoreRepository] is too
 * rigid for these scenarios — its cores list and staleness are fixed.
 */
private class SettableCoreRepository : CoreRepository {
    var cores: List<LibretroCore> = emptyList()
    var cachedCores: Set<String> = emptySet()
    val currentByName: MutableMap<String, Boolean?> = mutableMapOf()
    val downloadCalls = mutableListOf<String>()

    /** Pairs of (bytesDownloaded, totalBytes) emitted in order before the download completes. */
    var progressEmissions: List<Pair<Long, Long?>> = emptyList()

    /** When true, suspends downloadCore until [releaseDownload] is called. */
    var gateDownload: Boolean = false
    private val gate = CompletableDeferred<Unit>()

    fun releaseDownload() {
        gate.complete(Unit)
    }

    override suspend fun getAvailableCores(): Result<List<LibretroCore>> = Result.success(cores)
    override suspend fun getRecommendedCore(gameId: String): Result<LibretroCore> =
        cores.firstOrNull()?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("no cores"))

    override suspend fun downloadCore(
        coreName: String,
        customDownloadUrl: String?,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ): Result<String> {
        downloadCalls.add(coreName)
        if (gateDownload) gate.await()
        progressEmissions.forEach { (sent, total) -> onProgress(sent, total) }
        return Result.success("/fake/$coreName")
    }

    override suspend fun downloadCoreByHash(
        coreName: String,
        sha256: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
    ): Result<String> = Result.success("/fake/$coreName@$sha256")

    override suspend fun getLocalCorePath(coreName: String): String? =
        if (coreName in cachedCores) "/fake/cores/$coreName" else null

    override suspend fun isCoreCached(coreName: String): Boolean = coreName in cachedCores

    override suspend fun isCachedCoreCurrent(coreName: String): Boolean? =
        currentByName[coreName]

    override suspend fun getServerCoreSha(coreName: String): String? = null
}
