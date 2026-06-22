package com.spela.player.data

import com.spela.client.models.CatalogAvailability as DtoCatalogAvailability
import com.spela.client.models.CatalogConsoleCount as DtoCatalogConsoleCount
import com.spela.client.models.ImportJob as DtoImportJob
import com.spela.client.models.PresenceEntry as DtoPresenceEntry
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ImportStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class FederationMapperTest {
    private val epoch = Instant.fromEpochMilliseconds(0)

    private fun importDto(
        status: String,
        errorMessage: String = "",
        gameId: Long? = null,
        bytesDownloaded: Long = 0,
        totalBytes: Long = 0,
    ) = DtoImportJob(
        bytesDownloaded = bytesDownloaded,
        completedAt = null,
        console = "SNES",
        createdAt = epoch,
        errorMessage = errorMessage,
        gameId = gameId,
        id = 7,
        key = "igdb:1",
        requestedByUserId = 1,
        startedAt = null,
        status = status,
        title = "Chrono Trigger",
        totalBytes = totalBytes,
        updatedAt = epoch,
    )

    @Test
    fun catalogAvailabilityMapsCoverAndCounts() {
        val withCover = DtoCatalogAvailability(
            console = "SNES",
            cover = "https://x/co.jpg",
            key = "igdb:1",
            local = false,
            originCount = 3,
            title = "Chrono Trigger",
        ).toDomain()
        assertEquals("Chrono Trigger", withCover.title)
        assertEquals("SNES", withCover.console)
        assertEquals("https://x/co.jpg", withCover.coverUrl)
        assertEquals(3, withCover.originCount)
        assertFalse(withCover.local)

        // An empty cover string from the server becomes null (no cover).
        val noCover = DtoCatalogAvailability(
            console = "NES",
            cover = "",
            key = "igdb:2",
            local = true,
            originCount = 1,
            title = "Metroid",
        ).toDomain()
        assertNull(noCover.coverUrl)
        assertTrue(noCover.local)
    }

    @Test
    fun consoleCountMaps() {
        val c = DtoCatalogConsoleCount(console = "SNES", count = 12).toDomain()
        assertEquals("SNES", c.console)
        assertEquals(12, c.count)
    }

    @Test
    fun importJobMapsActiveStatusAndEmptyErrorToNull() {
        val job = importDto(status = "downloading", bytesDownloaded = 50, totalBytes = 100).toDomain()
        assertEquals(7L, job.id)
        assertEquals(ImportStatus.DOWNLOADING, job.status)
        assertTrue(job.status.isActive)
        assertNull(job.errorMessage)
        assertNull(job.gameId)
        assertEquals(50L, job.bytesDownloaded)
        assertEquals(100L, job.totalBytes)
    }

    @Test
    fun importJobMapsTerminalStates() {
        val completed = importDto(status = "completed", gameId = 42).toDomain()
        assertEquals(ImportStatus.COMPLETED, completed.status)
        assertFalse(completed.status.isActive)
        assertEquals(42L, completed.gameId)

        val failed = importDto(status = "failed", errorMessage = "no connected server").toDomain()
        assertEquals(ImportStatus.FAILED, failed.status)
        assertFalse(failed.status.isActive)
        assertEquals("no connected server", failed.errorMessage)
    }

    @Test
    fun unknownStatusMapsToUnknownAndIsNotActive() {
        val job = importDto(status = "some-future-status").toDomain()
        assertEquals(ImportStatus.UNKNOWN, job.status)
        assertFalse(job.status.isActive)
    }

    @Test
    fun presenceEntryMapsToDomainAndNarrowsHops() {
        val p = DtoPresenceEntry(
            gameKey = "igdb:1022",
            gameTitle = "Chrono Trigger",
            hops = 1L,
            originFingerprint = "",
            serverName = "Server B",
            username = "alice",
        ).toDomain()
        assertEquals("alice", p.username)
        assertEquals("igdb:1022", p.gameKey)
        assertEquals("Chrono Trigger", p.gameTitle)
        assertEquals("Server B", p.serverName)
        assertEquals(1, p.hops)
    }
}
