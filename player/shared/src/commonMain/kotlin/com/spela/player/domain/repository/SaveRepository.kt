package com.spela.player.domain.repository

import com.spela.player.domain.model.QuickSaveSlot
import com.spela.player.domain.model.SaveState
import com.spela.player.domain.model.StorageUsage

interface SaveRepository {
    suspend fun getSaveStates(gameId: String): Result<List<SaveState>>
    suspend fun uploadSaveState(gameId: String, name: String, data: ByteArray): Result<SaveState>
    suspend fun uploadSaveStateWithScreenshot(gameId: String, name: String, data: ByteArray, screenshot: ByteArray?): Result<SaveState>
    suspend fun downloadSaveState(gameId: String, saveId: String): Result<ByteArray>
    suspend fun deleteSaveState(gameId: String, saveId: String): Result<Unit>
    suspend fun uploadAutoSave(gameId: String, data: ByteArray): Result<SaveState>
    suspend fun uploadAutoSaveWithScreenshot(gameId: String, data: ByteArray, screenshot: ByteArray?): Result<SaveState>
    suspend fun downloadAutoSave(gameId: String): Result<ByteArray>
    suspend fun saveLocally(gameId: String, name: String, data: ByteArray, isAuto: Boolean): Result<SaveState>
    suspend fun loadLocalAutoSave(gameId: String): Result<ByteArray>
    suspend fun getPendingSyncCount(): Int

    // Feature 2: Rename save states
    suspend fun renameSaveState(gameId: String, saveId: String, name: String): Result<Unit>

    // Feature 4: Save state notes
    suspend fun updateSaveNotes(gameId: String, saveId: String, notes: String): Result<Unit>

    // Feature 5: Quick-save slots
    suspend fun saveToSlot(gameId: String, slot: Int, data: ByteArray, screenshot: ByteArray? = null): Result<SaveState>
    suspend fun loadFromSlot(gameId: String, slot: Int): Result<ByteArray>
    suspend fun getSlots(gameId: String): Result<List<QuickSaveSlot>>

    // Feature 6: Multiple auto-save history
    suspend fun getAutoSaveHistory(gameId: String): Result<List<SaveState>>

    // Feature 7: Bulk delete saves
    suspend fun bulkDeleteSaves(gameId: String, saveIds: List<Long>): Result<Int>

    // Feature 8: Storage usage
    suspend fun getStorageUsage(): Result<StorageUsage>

    // Feature 9: Import save
    suspend fun importSaveState(gameId: String, name: String, fileData: ByteArray): Result<SaveState>
}
