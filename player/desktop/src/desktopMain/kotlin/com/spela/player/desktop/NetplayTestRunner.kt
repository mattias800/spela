package com.spela.player.desktop

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.client.models.AuthLoginRequest
import com.spela.client.models.CreateNetplaySessionRequest
import com.spela.client.models.JoinByInviteCodeRequest
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.libretro.LibretroJni
import com.spela.player.netplay.NetplayInputBuffer
import com.spela.player.netplay.NetplaySignaling
import com.spela.player.netplay.WebSocketRelayTransport
import com.spela.player.platform.DesktopFileStorage
import com.spela.player.util.DispatcherProvider
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.takeWhile
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless netplay emulator process for E2E testing.
 *
 * Runs a libretro core + ROM in netplay lockstep mode, controlled via stdin commands.
 * Two instances of this program (host + client) connect to the same server and
 * play the same game in sync. A shell script orchestrates both processes.
 *
 * Usage:
 *   NetplayTestRunner --server-url URL --username USER --password PASS
 *                     --role host|client --invite-code CODE
 *                     --core-path PATH --rom-path PATH
 *                     [--input-delay N]
 *
 * Stdin commands (one per line):
 *   press <button>        - Press and release a button (select, start, a, b, up, down, left, right)
 *   screenshot <path>     - Save current video frame as PNG
 *   wait <ms>             - Sleep for N milliseconds
 *   wait-frames <n>       - Wait for N emulation frames
 *   quit                  - Stop emulation and exit
 *
 * Stdout protocol:
 *   READY                 - Emulation is running, accepting commands
 *   OK <command>          - Command completed successfully
 *   ERROR <message>       - Something failed
 */
fun main(args: Array<String>) {
    val config = parseArgs(args)
    runBlocking {
        try {
            NetplayTestRunnerImpl(config).run()
        } catch (e: Exception) {
            println("ERROR ${e.message}")
            System.exit(1)
        }
    }
}

private data class Config(
    val serverUrl: String,
    val username: String,
    val password: String,
    val role: String, // "host" or "client"
    val inviteCode: String?, // null for host (will create), required for client
    val corePath: String,
    val romPath: String,
    val inputDelay: Int = 3,
    val solo: Boolean = false, // skip netplay, run single-player (debug mode)
)

private fun parseArgs(args: Array<String>): Config {
    val map = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        if (args[i].startsWith("--") && i + 1 < args.size) {
            map[args[i].removePrefix("--")] = args[i + 1]
            i += 2
        } else {
            i++
        }
    }
    return Config(
        serverUrl = map["server-url"] ?: error("--server-url required"),
        username = map["username"] ?: error("--username required"),
        password = map["password"] ?: error("--password required"),
        role = map["role"] ?: error("--role required (host or client)"),
        inviteCode = map["invite-code"],
        corePath = map["core-path"] ?: error("--core-path required"),
        romPath = map["rom-path"] ?: error("--rom-path required"),
        inputDelay = map["input-delay"]?.toIntOrNull() ?: 3,
        solo = map["solo"] == "true",
    )
}


private class NetplayTestRunnerImpl(private val config: Config) {
    private val fileStorage = DesktopFileStorage()
    private val jni = LibretroJni()
    private val controller = DesktopLibretroController(jni, fileStorage)
    private val tokenManager = TokenManager()
    private val apiClient = SpelaApiClient(
        engineFactory = CIO,
        tokenManager = tokenManager,
    )

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val io = Dispatchers.IO
        override val default = Dispatchers.Default
    }

    @Volatile
    private var running = false

    @Volatile
    private var emulationStarted = false

    suspend fun run() {
        log("Starting netplay test runner as ${config.role}")

        // 1. Configure API client
        apiClient.setBaseUrl(config.serverUrl)

        // 2. Login
        log("Logging in as ${config.username}...")
        val authResponse = apiClient.login(
            AuthLoginRequest(username = config.username, password = config.password),
        )
        tokenManager.setTokens(authResponse.accessToken, authResponse.refreshToken)
        log("Logged in successfully")

        // 3. Create or join session
        val sessionId: String
        val localPort: Int

        if (config.role == "host") {
            // Find the game ID for Chip 'n Dale by listing NES games.
            log("Finding game on server...")
            val gamesResponse = apiClient.getGamesForConsole("nes", page = 1, pageSize = 100)
            val games = gamesResponse.data.orEmpty()
            val game = games.firstOrNull { it.title.contains("Chip", ignoreCase = true) }
                ?: games.firstOrNull { it.title.contains("Rescue", ignoreCase = true) }
                ?: error("Could not find Chip 'n Dale game on server. Available: ${games.map { it.title }}")

            log("Creating netplay session for '${game.title}' (ID: ${game.id})...")
            val session = apiClient.createNetplaySession(
                CreateNetplaySessionRequest(gameId = game.id, inputDelay = config.inputDelay.toLong())
            )
            sessionId = session.id
            localPort = 0 // Host is always port 0
            // Print invite code for the orchestration script to capture
            println("INVITE_CODE ${session.inviteCode}")
            System.out.flush()
            log("Session created: ${session.id}, invite code: ${session.inviteCode}")
            log("Waiting for client to join...")
        } else {
            val code = config.inviteCode ?: error("--invite-code required for client role")
            log("Joining session with invite code: $code...")
            val session = apiClient.joinNetplayByInviteCode(JoinByInviteCodeRequest(inviteCode = code))
            sessionId = session.id
            localPort = 1 // Client is always port 1
            log("Joined session: ${session.id}")
        }

        // 4. Load core and ROM
        log("Loading core: ${config.corePath}")
        controller.loadCore(config.corePath)
        log("Loading ROM: ${config.romPath}")
        controller.loadGame(config.romPath)

        // 5. Set up netplay transport (skip in solo mode)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        var inputCollectorJob: Job? = null

        if (!config.solo) {
            log("Setting up netplay transport...")
            val signaling = NetplaySignaling(
                apiClient = apiClient,
                engineFactory = CIO,
                dispatchers = dispatchers,
                scope = scope,
                sessionId = sessionId,
            )
            val transport = WebSocketRelayTransport(signaling, scope)
            val inputBuffer = NetplayInputBuffer()

            // Connect transport
            transport.connect()
            delay(2000) // Let WebSocket establish and both sides connect

            // Skip state transfer — both sides loaded the same ROM and are at
            // the same initial state. State sync would be needed for mid-game
            // joins but for E2E testing from fresh start it's unnecessary.
            log("Both sides loaded same ROM — skipping state transfer")

            // Configure lockstep mode
            controller.setNetplayMode(transport, inputBuffer, localPort, config.inputDelay)

            // Start collecting remote inputs
            inputCollectorJob = scope.launch(Dispatchers.IO) {
                transport.remoteInputs.collect { remote ->
                    inputBuffer.setRemoteInput(remote.frame, remote.port, remote.input)
                }
            }
        } else {
            log("Solo mode — skipping netplay setup")
        }

        // Give collectors time to subscribe to SharedFlows
        delay(100)

        // Signal ready — both sides must be READY before starting emulation
        running = true
        println("READY")
        System.out.flush()
        log("Waiting for 'go' command to start emulation...")

        // 7. Read and execute stdin commands
        // Wait for "go" command before starting emulation
        val reader = System.`in`.bufferedReader()
        while (running) {
            val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
            val parts = line.trim().split(" ", limit = 2)
            if (parts.isEmpty()) continue

            try {
                when (parts[0].lowercase()) {
                    "go" -> {
                        if (!emulationStarted) {
                            log("Starting emulation...")
                            emulationStarted = true
                            controller.start()
                            if (!config.solo) {
                                controller.startNetplayInputSync()
                            }
                        }
                        println("OK go")
                    }
                    "press" -> {
                        val button = parts.getOrNull(1) ?: error("Usage: press <button>")
                        pressButton(localPort, button)
                        println("OK press $button")
                    }
                    "screenshot" -> {
                        val path = parts.getOrNull(1) ?: error("Usage: screenshot <path>")
                        takeScreenshot(path)
                        println("OK screenshot $path")
                    }
                    "wait" -> {
                        val ms = parts.getOrNull(1)?.toLongOrNull() ?: error("Usage: wait <ms>")
                        delay(ms)
                        println("OK wait $ms")
                    }
                    "wait-frames" -> {
                        val n = parts.getOrNull(1)?.toIntOrNull() ?: error("Usage: wait-frames <n>")
                        // Wait approximately N frames at 60fps
                        delay((n * 16.67).toLong())
                        println("OK wait-frames $n")
                    }
                    "quit" -> {
                        println("OK quit")
                        running = false
                    }
                    else -> println("ERROR unknown command: ${parts[0]}")
                }
            } catch (e: Exception) {
                println("ERROR ${e.message}")
            }
            System.out.flush()
        }

        // 8. Cleanup
        log("Shutting down...")
        controller.stop()
        inputCollectorJob?.cancel()
        scope.cancel()
        log("Done")
    }

    private fun pressButton(port: Int, button: String) {
        val buttonId = when (button.lowercase()) {
            "b" -> 0
            "y" -> 1
            "select" -> 2
            "start" -> 3
            "up" -> 4
            "down" -> 5
            "left" -> 6
            "right" -> 7
            "a" -> 8
            "x" -> 9
            "l" -> 10
            "r" -> 11
            else -> error("Unknown button: $button")
        }
        // Use controller.setButton which writes to both JNI and the Kotlin-side
        // netplayLocalButtons variable (read by captureNetplayLocalInput in the
        // netplay emulation loop, avoiding the JNI feedback loop).
        val holdMs = 200L // ~10 frames at 50fps
        log("pressButton: port=$port buttonId=$buttonId holding for ${holdMs}ms")
        controller.setButton(port, buttonId, true)
        Thread.sleep(holdMs)
        controller.setButton(port, buttonId, false)
        log("pressButton: released")
    }

    private fun takeScreenshot(path: String) {
        val width = jni.nativeGetVideoWidth()
        val height = jni.nativeGetVideoHeight()
        if (width <= 0 || height <= 0) error("Invalid video dimensions: ${width}x${height}")

        val frame: ByteArray
        val isBgra: Boolean

        if (jni.nativeGpuIsActive()) {
            // GPU renderer is active — read back from GPU as BGRA
            val buf = ByteArray(width * height * 4)
            val result = jni.nativeGpuRenderToBgra(buf)
            if (result == 0L) error("GPU readback failed")
            frame = buf
            isBgra = true
        } else {
            // Software path — use the frame buffer directly
            frame = jni.nativeGetVideoFrame() ?: error("No video frame available")
            isBgra = false
        }

        val expectedPixels = width * height
        val actualBpp = frame.size / expectedPixels
        // Use BGRA if GPU is active or if buffer is 4bpp
        val pixelFormat = if (isBgra || actualBpp >= 4) 2 else jni.nativeGetPixelFormat()

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = (y * width + x)
                val rgb = when (pixelFormat) {
                    0 -> { // XRGB1555
                        val pixel = (frame[offset * 2 + 1].toInt() and 0xFF shl 8) or
                            (frame[offset * 2].toInt() and 0xFF)
                        val r = ((pixel shr 10) and 0x1F) * 255 / 31
                        val g = ((pixel shr 5) and 0x1F) * 255 / 31
                        val b = (pixel and 0x1F) * 255 / 31
                        (r shl 16) or (g shl 8) or b
                    }
                    1 -> { // RGB565
                        val pixel = (frame[offset * 2 + 1].toInt() and 0xFF shl 8) or
                            (frame[offset * 2].toInt() and 0xFF)
                        val r = ((pixel shr 11) and 0x1F) * 255 / 31
                        val g = ((pixel shr 5) and 0x3F) * 255 / 63
                        val b = (pixel and 0x1F) * 255 / 31
                        (r shl 16) or (g shl 8) or b
                    }
                    2 -> { // XRGB8888 / BGRA
                        val b2 = frame[offset * 4].toInt() and 0xFF
                        val g2 = frame[offset * 4 + 1].toInt() and 0xFF
                        val r2 = frame[offset * 4 + 2].toInt() and 0xFF
                        (r2 shl 16) or (g2 shl 8) or b2
                    }
                    else -> 0
                }
                image.setRGB(x, y, rgb)
            }
        }

        val file = File(path)
        file.parentFile?.mkdirs()
        ImageIO.write(image, "PNG", file)
        log("Screenshot saved: $path (${width}x${height}, format=$pixelFormat, gpu=${jni.nativeGpuIsActive()})")
    }

    private fun sendStateChunks(transport: WebSocketRelayTransport, stateData: ByteArray) {
        val chunkSize = 16_384
        val totalChunks = (stateData.size + chunkSize - 1) / chunkSize
        for (i in 0 until totalChunks) {
            val offset = i * chunkSize
            val end = minOf(offset + chunkSize, stateData.size)
            val chunk = stateData.copyOfRange(offset, end)
            val encoded = com.spela.player.netplay.NetplayProtocol.encodeStateChunk(
                chunkIndex = i, totalChunks = totalChunks, totalSize = stateData.size, data = chunk,
            )
            transport.sendBinary(encoded)
        }
    }

    private suspend fun receiveStateChunks(transport: WebSocketRelayTransport): ByteArray? {
        val receivedChunks = mutableMapOf<Int, ByteArray>()
        var totalChunks = -1
        var totalSize = -1

        kotlinx.coroutines.withTimeoutOrNull(10_000) {
            transport.remoteBinary.takeWhile { msg ->
                val chunk = com.spela.player.netplay.NetplayProtocol.decodeStateChunk(msg.data)
                if (chunk != null) {
                    totalChunks = chunk.totalChunks
                    totalSize = chunk.totalSize
                    receivedChunks[chunk.chunkIndex] = chunk.data
                }
                receivedChunks.size < totalChunks || totalChunks < 0
            }.collect {}
        } ?: return null

        if (totalSize < 0 || receivedChunks.size < totalChunks) return null

        val result = ByteArray(totalSize)
        var offset = 0
        for (i in 0 until totalChunks) {
            val chunkData = receivedChunks[i] ?: return null
            chunkData.copyInto(result, offset)
            offset += chunkData.size
        }
        return result
    }

    private fun log(msg: String) {
        System.err.println("[${config.role}] $msg")
    }
}
