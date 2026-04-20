package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import kotlin.test.assertTrue

/**
 * Canonical GameResponse JSON for mock HTTP responses. Includes every
 * @Required field — kotlinx-serialization's strict mode rejects payloads
 * missing any of them.
 */
private fun gameJson(
    id: String = "10",
    title: String = "Super Mario Bros",
    isFavorite: Boolean = false,
    lastPlayedAt: String? = null,
    totalPlayTime: Long = 0,
    screenshotUrls: List<String>? = null,
): String {
    val screenshots = screenshotUrls?.joinToString(",", "[", "]") { "\"$it\"" } ?: "[]"
    val lastPlayed = lastPlayedAt?.let { "\"$it\"" } ?: "null"
    return """{"id":"$id","title":"$title","consoleId":"1","consoleName":"NES",""" +
        """"achievementsWarning":"","ageRatings":[],"averageRating":0,"biosStatus":"",""" +
        """"coreOverride":"","coverAspectRatio":0.75,"coverUrl":"/covers/smb.png",""" +
        """"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z",""" +
        """"description":"A classic platformer","developer":"Nintendo",""" +
        """"discCount":0,"discs":[],"fileName":"smb.nes","fileSize":40960,""" +
        """"gameModes":"","genre":"Platformer","groupKey":"","heroUrl":"",""" +
        """"igdbCriticsRating":0,"igdbUserRating":0,"igdbUserRatingCount":0,""" +
        """"isFavorite":$isFavorite,"isInPlayLater":false,"isPreRelease":false,""" +
        """"languageSupports":[],"lastPlayedAt":$lastPlayed,"logoUrl":"",""" +
        """"parentGame":{"id":"","title":"","coverUrl":""},"partyInfo":"",""" +
        """"playable":true,"players":1,"publisher":"Nintendo",""" +
        """"ratingCount":0,"region":"","releaseDate":"1985-09-13","releaseDates":[],""" +
        """"revision":"","romHacks":[],"scrapeAttempts":0,"scraperId":"",""" +
        """"screenshotUrls":$screenshots,"storyline":"","tags":"",""" +
        """"timeToBeatCompletely":0,"timeToBeatHastily":0,"timeToBeatNormally":0,""" +
        """"totalPlayTime":$totalPlayTime,"totalRating":0,"totalRatingCount":0,""" +
        """"userRating":null,"variantCount":0,"variants":[],""" +
        """"verificationStatus":"","verificationTag":"","videos":[]}"""
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameRepositoryOfflineTest {

    private lateinit var database: SpelaDatabase

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private val workingEngineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine(MockEngineConfig().apply {
                addHandler { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/api/consoles") -> respond(
                            // Full ConsoleResponse shape — the generated
                            // @Required fields are all present; kotlinx-
                            // serialization's strict mode otherwise rejects
                            // the payload (unlike the old hand-written
                            // ConsoleDto, which had defaults everywhere).
                            """[{"id":"1","name":"NES","abbreviation":"nes","code":"nes","gameCount":5,"colorTheme":"#e74c3c","coverAspectRatio":0.72,"defaultCore":"fceumm","emulatorJsCore":"fceumm","extensions":["nes"],"generation":3,"iconUrl":"/images/nes.png","logoUrl":"/images/nes.svg","logoPngUrl":"/images/nes.png","saveStateSupport":true,"browserPlayable":true,"playable":true,"maker":{"code":"nintendo","name":"Nintendo"},"mediaType":{"code":"cart","name":"Cartridge","category":{"code":"physical","name":"Physical"}},"releaseYear":1983,"summary":"8-bit classic","unitsSold":61900000,"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}]""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        path.contains("/api/consoles/") && path.endsWith("/games") -> respond(
                            "[${gameJson()}]",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        path.endsWith("/api/games") -> respond(
                            """{"data":[${gameJson()}],"total":1,"page":1,"pageSize":20}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        path.matches(Regex(".*/api/games/\\d+$")) -> respond(
                            gameJson(screenshotUrls = listOf("/screenshots/smb1.png")),
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        path.endsWith("/api/user/recent") -> respond(
                            "[${gameJson(lastPlayedAt = "2026-02-20T10:00:00Z", totalPlayTime = 3600L)}]",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        path.endsWith("/api/user/favorites") -> respond(
                            "[${gameJson(isFavorite = true)}]",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                        else -> respond("", HttpStatusCode.OK)
                    }
                }
                block()
            })
        }
    }

    private val failingEngineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine {
            return MockEngine(MockEngineConfig().apply {
                addHandler { throw java.net.ConnectException("Offline") }
                block()
            })
        }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createRepo(
        engineFactory: HttpClientEngineFactory<*>,
        scope: CoroutineScope,
    ): GameRepositoryImpl {
        val apiClient = SpelaApiClient(engineFactory, TokenManager())
        val connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        return GameRepositoryImpl(
            apiClient = apiClient,
            database = database,
            connectivityMonitor = connectivityMonitor,
        )
    }

    private fun prepopulateConsole() {
        database.spelaDatabaseQueries.insertCachedConsole(
            id = "1",
            name = "NES",
            abbreviation = "nes",
            game_count = 5,
            color_theme = "#e74c3c",
            cover_aspect_ratio = 0.72,
            default_core = "fceumm",
            icon_url = "/images/nes.png",
            logo_url = "/api/consoles/nes/logo",
            save_state_support = 1,
        )
    }

    private fun prepopulateGame(
        id: String = "10",
        consoleId: String = "1",
        title: String = "Super Mario Bros",
        isFavorite: Boolean = false,
        lastPlayedAt: String? = null,
        totalPlayTime: Long = 0,
    ) {
        database.spelaDatabaseQueries.insertCachedGame(
            id = id,
            console_id = consoleId,
            title = title,
            console_name = "NES",
            cover_url = "/covers/smb.png",
            description = "A classic platformer",
            developer = "Nintendo",
            publisher = "Nintendo",
            release_date = "1985-09-13",
            genre = "Platformer",
            file_size = 40960,
            file_name = "smb.nes",
            cover_aspect_ratio = 0.714,
            disc_count = 0,
            is_favorite = if (isFavorite) 1L else 0L,
            is_in_play_later = 0,
            last_played_at = lastPlayedAt,
            total_play_time = totalPlayTime,
            cached_at = System.currentTimeMillis(),
        )
    }

    // --- Test 1: getConsoles caches to DB on success ---

    @Test
    fun getConsolesCachesToDbOnSuccess() = runTest(testDispatcher) {
        val repo = createRepo(workingEngineFactory, this)

        val result = repo.getConsoles()

        assertTrue(result.isSuccess)
        val consoles = result.getOrThrow()
        assertEquals(1, consoles.size)
        assertEquals("NES", consoles[0].name)
        assertEquals("nes", consoles[0].abbreviation)

        // Verify DB was populated
        val cached = database.spelaDatabaseQueries.getAllCachedConsoles().executeAsList()
        assertEquals(1, cached.size)
        assertEquals("1", cached[0].id)
        assertEquals("NES", cached[0].name)
    }

    // --- Test 2: getConsoles returns cache when API throws ---

    @Test
    fun getConsolesReturnsCacheWhenApiThrows() = runTest(testDispatcher) {
        prepopulateConsole()

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getConsoles()

        assertTrue(result.isSuccess)
        val consoles = result.getOrThrow()
        assertEquals(1, consoles.size)
        assertEquals("NES", consoles[0].name)
        assertEquals("nes", consoles[0].abbreviation)
    }

    // --- Test 3: getConsoles fails when cache empty AND API fails ---

    @Test
    fun getConsolesFailsWhenCacheEmptyAndApiFails() = runTest(testDispatcher) {
        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getConsoles()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.net.ConnectException)
    }

    // --- Test 4: getGamesForConsole caches on success ---

    @Test
    fun getGamesForConsoleCachesOnSuccess() = runTest(testDispatcher) {
        val repo = createRepo(workingEngineFactory, this)

        val result = repo.getGamesForConsole("1")

        assertTrue(result.isSuccess)
        val games = result.getOrThrow()
        assertEquals(1, games.size)
        assertEquals("Super Mario Bros", games[0].title)

        // Verify DB was populated
        val cached = database.spelaDatabaseQueries.getCachedGamesForConsole("1").executeAsList()
        assertEquals(1, cached.size)
        assertEquals("10", cached[0].id)
        assertEquals("Super Mario Bros", cached[0].title)
    }

    // --- Test 5: getGamesForConsole returns cache when API fails ---

    @Test
    fun getGamesForConsoleReturnsCacheWhenApiFails() = runTest(testDispatcher) {
        prepopulateGame()

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getGamesForConsole("1")

        assertTrue(result.isSuccess)
        val games = result.getOrThrow()
        assertEquals(1, games.size)
        assertEquals("Super Mario Bros", games[0].title)
        assertEquals("1", games[0].consoleId)
    }

    // --- Test 6: searchGames falls back to LIKE query ---

    @Test
    fun searchGamesFallsBackToLikeQuery() = runTest(testDispatcher) {
        prepopulateGame(id = "10", title = "Super Mario Bros")
        prepopulateGame(id = "20", title = "Zelda", consoleId = "1")

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.searchGames("Mario")

        assertTrue(result.isSuccess)
        val games = result.getOrThrow()
        assertEquals(1, games.size)
        assertEquals("Super Mario Bros", games[0].title)
    }

    // --- Test 7: getGameDetail returns cache when API fails ---

    @Test
    fun getGameDetailReturnsCacheWhenApiFails() = runTest(testDispatcher) {
        prepopulateGame()

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getGameDetail("10")

        assertTrue(result.isSuccess)
        val detail = result.getOrThrow()
        assertEquals("Super Mario Bros", detail.game.title)
        assertEquals("1", detail.game.consoleId)
        // Cached fallback has no screenshots
        assertTrue(detail.screenshots.isEmpty())
    }

    // --- Test 8: getFavoriteGames returns cached favorites ---

    @Test
    fun getFavoriteGamesReturnsCachedFavorites() = runTest(testDispatcher) {
        prepopulateGame(id = "10", title = "Super Mario Bros", isFavorite = true)
        prepopulateGame(id = "20", title = "Zelda", isFavorite = false, consoleId = "1")

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getFavoriteGames()

        assertTrue(result.isSuccess)
        val games = result.getOrThrow()
        assertEquals(1, games.size)
        assertEquals("Super Mario Bros", games[0].title)
        assertTrue(games[0].isFavorite)
    }

    // --- Test 9: getRecentGames returns cached recent games ---

    @Test
    fun getRecentGamesReturnsCachedRecentGames() = runTest(testDispatcher) {
        prepopulateGame(
            id = "10",
            title = "Super Mario Bros",
            lastPlayedAt = "2026-02-20T10:00:00Z",
            totalPlayTime = 3600,
        )
        prepopulateGame(id = "20", title = "Zelda", lastPlayedAt = null, consoleId = "1")

        val repo = createRepo(failingEngineFactory, this)
        val result = repo.getRecentGames()

        assertTrue(result.isSuccess)
        val games = result.getOrThrow()
        assertEquals(1, games.size)
        assertEquals("Super Mario Bros", games[0].title)
        assertEquals("2026-02-20T10:00:00Z", games[0].lastPlayedAt)
        assertEquals(3600L, games[0].totalPlayTime)
    }
}
