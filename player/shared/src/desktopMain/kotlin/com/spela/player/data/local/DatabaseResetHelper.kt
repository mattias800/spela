package com.spela.player.data.local

import java.io.File

actual object DatabaseResetHelper {
    actual fun resetDatabase() {
        val dbPath = "spela.db"
        File(dbPath).delete()
        File("$dbPath-wal").delete()
        File("$dbPath-shm").delete()
        DatabaseHealthCheck.clearError()
    }
}
