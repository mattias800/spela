package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaveRepositoryOfflineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var database: SpelaDatabase
    private lateinit var fileStorage: InMemoryFileStorage
    private lateinit var connectivityMonitor: ConnectivityMonitor
    private lateinit var repo: SaveRepositoryImpl
    private lateinit var testScope: CoroutineScope

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        fileStorage = InMemoryFileStorage()
        testScope = CoroutineScope(testDispatcher)
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, testScope)
        repo = SaveRepositoryImpl(apiClient, database, fileStorage, connectivityMonitor)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uploadSaveStateOfflineSavesLocally() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        val data = "save-data".toByteArray()

        val result = repo.uploadSaveState("42", "My Save", data)

        assertTrue(result.isSuccess)
        val entities = database.spelaDatabaseQueries.getLocalSaveStatesForGame("42").executeAsList()
        assertEquals(1, entities.size)
        assertEquals("My Save", entities[0].name)
        assertEquals("42", entities[0].game_id)
        // Verify file was written
        assertTrue(fileStorage.files.any { it.value.contentEquals(data) })
    }

    @Test
    fun uploadSaveStateOfflineDoesNotThrow() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        val data = "offline-save".toByteArray()

        val result = repo.uploadSaveState("10", "Offline Save", data)

        assertTrue(result.isSuccess)
        val save = result.getOrThrow()
        assertEquals("Offline Save", save.name)
    }

    @Test
    fun uploadAutoSaveReplacesPreviousLocalAutoSave() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        repo.uploadAutoSave("7", "first-auto".toByteArray())
        repo.uploadAutoSave("7", "second-auto".toByteArray())

        val autoSaves = database.spelaDatabaseQueries
            .getLocalSaveStatesForGame("7").executeAsList()
            .filter { it.is_auto == 1L }
        assertEquals(1, autoSaves.size)
    }

    @Test
    fun getSaveStatesOfflineReturnsLocalSaves() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "local-1",
            game_id = "55",
            name = "Local Save",
            local_path = "/tmp/saves/savestates/55/local-1.sav",
            file_size = 100,
            is_auto = 0,
            created_at = nowMillis,
            updated_at = nowMillis,
            server_id = null,
            sync_status = "pending",
            last_synced_at = null,
        )

        val result = repo.getSaveStates("55")

        assertTrue(result.isSuccess)
        val saves = result.getOrThrow()
        assertEquals(1, saves.size)
        assertEquals("Local Save", saves[0].name)
    }

    @Test
    fun downloadSaveStateReturnsLocalFile() = runTest(testDispatcher) {
        val data = "local-file-content".toByteArray()
        val path = "/tmp/saves/savestates/20/local-2.sav"
        fileStorage.files[path] = data

        val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "local-2",
            game_id = "20",
            name = "Test Save",
            local_path = path,
            file_size = data.size.toLong(),
            is_auto = 0,
            created_at = nowMillis,
            updated_at = nowMillis,
            server_id = null,
            sync_status = "pending",
            last_synced_at = null,
        )

        val result = repo.downloadSaveState("20", "local-2")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contentEquals(data))
    }

    @Test
    fun downloadAutoSaveReturnsLocalAutoSave() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        val data = "auto-save-bytes".toByteArray()

        repo.saveLocally("33", "Auto Save", data, isAuto = true)

        val result = repo.downloadAutoSave("33")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().contentEquals(data))
    }

    @Test
    fun downloadAutoSaveOfflineNoLocalFails() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        val result = repo.downloadAutoSave("99")

        assertTrue(result.isFailure)
    }

    @Test
    fun saveLocallyWritesFileAndDbEntry() = runTest(testDispatcher) {
        val data = "raw-save-data".toByteArray()

        val result = repo.saveLocally("15", "Manual Save", data, isAuto = false)

        assertTrue(result.isSuccess)
        val save = result.getOrThrow()
        assertEquals("Manual Save", save.name)

        // Verify DB entry exists
        val entities = database.spelaDatabaseQueries.getLocalSaveStatesForGame("15").executeAsList()
        assertEquals(1, entities.size)
        assertEquals("Manual Save", entities[0].name)
        assertEquals(data.size.toLong(), entities[0].file_size)

        // Verify file was written
        val filePath = entities[0].local_path
        assertTrue(fileStorage.files.containsKey(filePath))
        assertTrue(fileStorage.files[filePath]!!.contentEquals(data))
    }

    @Test
    fun getPendingSyncCountCountsPending() = runTest(testDispatcher) {
        val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "pending-1", game_id = "1", name = "Save 1",
            local_path = "/tmp/saves/1.sav", file_size = 10, is_auto = 0,
            created_at = nowMillis, updated_at = nowMillis,
            server_id = null, sync_status = "pending", last_synced_at = null,
        )
        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "pending-2", game_id = "2", name = "Save 2",
            local_path = "/tmp/saves/2.sav", file_size = 20, is_auto = 0,
            created_at = nowMillis, updated_at = nowMillis,
            server_id = null, sync_status = "pending", last_synced_at = null,
        )
        database.spelaDatabaseQueries.insertLocalSaveState(
            id = "synced-1", game_id = "3", name = "Save 3",
            local_path = "/tmp/saves/3.sav", file_size = 30, is_auto = 0,
            created_at = nowMillis, updated_at = nowMillis,
            server_id = 100, sync_status = "synced", last_synced_at = nowMillis,
        )

        val count = repo.getPendingSyncCount()

        assertEquals(2, count)
    }

    @Test
    fun deleteSaveStateDeletesLocalFileAndDbEntry() = runTest(testDispatcher) {
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)
        val data = "to-be-deleted".toByteArray()

        val saveResult = repo.saveLocally("50", "Delete Me", data, isAuto = false)
        assertTrue(saveResult.isSuccess)

        val entities = database.spelaDatabaseQueries.getLocalSaveStatesForGame("50").executeAsList()
        assertEquals(1, entities.size)
        val localId = entities[0].id
        val localPath = entities[0].local_path

        // Verify file exists before delete
        assertTrue(fileStorage.files.containsKey(localPath))

        val deleteResult = repo.deleteSaveState("50", localId)

        assertTrue(deleteResult.isSuccess)
        // Verify DB entry removed
        val remaining = database.spelaDatabaseQueries.getLocalSaveStatesForGame("50").executeAsList()
        assertTrue(remaining.isEmpty())
        // Verify file removed
        assertTrue(!fileStorage.files.containsKey(localPath))
    }

    private class InMemoryFileStorage : FileStorage {
        val files = mutableMapOf<String, ByteArray>()
        override fun getGamesDir() = "/tmp/games"
        override fun getCoresDir() = "/tmp/cores"
        override fun getSavesDir() = "/tmp/saves"
        override fun getBiosDir() = "/tmp/bios"
        override suspend fun createDirectory(path: String) {}
        override suspend fun writeFile(path: String, data: ByteArray) { files[path] = data }
        override suspend fun readFile(path: String) = files[path] ?: throw Exception("Not found: $path")
        override suspend fun fileExists(path: String) = files.containsKey(path)
        override suspend fun deleteFile(path: String) { files.remove(path) }
        override suspend fun deleteDirectory(path: String) { files.keys.removeAll { it.startsWith(path) } }
        override suspend fun getDirectorySize(path: String) = 0L
        override suspend fun writeFileStreaming(path: String, writer: suspend (suspend (ByteArray, Int, Int) -> Unit) -> Unit) {
            val chunks = mutableListOf<ByteArray>()
            writer { bytes, offset, length -> chunks.add(bytes.copyOfRange(offset, offset + length)) }
            files[path] = chunks.fold(ByteArray(0)) { acc, chunk -> acc + chunk }
        }
        override suspend fun getFileSize(path: String) = files[path]?.size?.toLong() ?: 0L
        override suspend fun listFiles(path: String) = emptyList<String>()
        override suspend fun isDirectory(path: String) = false
    }
}
