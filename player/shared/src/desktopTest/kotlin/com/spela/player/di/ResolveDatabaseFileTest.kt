package com.spela.player.di

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolveDatabaseFileTest {

    private val dataDir = Files.createTempDirectory("spela-db-data").toFile()
    private val legacyDir = Files.createTempDirectory("spela-db-legacy").toFile()

    @AfterTest
    fun cleanup() {
        dataDir.deleteRecursively()
        legacyDir.deleteRecursively()
    }

    @Test
    fun returnsDataDirPathWhenNoLegacyDbExists() {
        val result = resolveDatabaseFile(dataDir, File(legacyDir, "spela.db"))
        assertEquals(File(dataDir, "spela.db"), result)
        assertFalse(result.exists())
    }

    @Test
    fun migratesLegacyDbAndSidecarsIntoDataDir() {
        val legacyDb = File(legacyDir, "spela.db").apply { writeText("db") }
        File(legacyDir, "spela.db-wal").writeText("wal")
        File(legacyDir, "spela.db-shm").writeText("shm")

        val result = resolveDatabaseFile(dataDir, legacyDb)

        assertEquals(File(dataDir, "spela.db"), result)
        assertEquals("db", result.readText())
        assertEquals("wal", File(dataDir, "spela.db-wal").readText())
        assertEquals("shm", File(dataDir, "spela.db-shm").readText())
        assertFalse(legacyDb.exists())
        assertFalse(File(legacyDir, "spela.db-wal").exists())
        assertFalse(File(legacyDir, "spela.db-shm").exists())
    }

    @Test
    fun neverOverwritesAnExistingDataDirDb() {
        File(dataDir, "spela.db").writeText("current")
        val legacyDb = File(legacyDir, "spela.db").apply { writeText("stale") }

        val result = resolveDatabaseFile(dataDir, legacyDb)

        assertEquals("current", result.readText())
        assertTrue(legacyDb.exists())
    }

    @Test
    fun legacyPathEqualToTargetIsANoOp() {
        val db = File(dataDir, "spela.db").apply { writeText("db") }
        val result = resolveDatabaseFile(dataDir, db)
        assertEquals(db, result)
        assertEquals("db", result.readText())
    }
}
