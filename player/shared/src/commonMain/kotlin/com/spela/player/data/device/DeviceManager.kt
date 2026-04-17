package com.spela.player.data.device

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.client.models.RegisterDeviceRequest
import com.spela.player.util.currentPlatform
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

open class DeviceManager(
    private val database: SpelaDatabase,
    private val apiClient: SpelaApiClient,
) {
    private val queries = database.spelaDatabaseQueries

    fun getDeviceUuid(): String {
        return queries.getDeviceInfo().executeAsOneOrNull()?.device_uuid
            ?: generateAndStoreUuid()
    }

    fun getDeviceName(): String {
        return queries.getDeviceInfo().executeAsOneOrNull()?.device_name ?: ""
    }

    fun getServerDeviceId(): Long? {
        return queries.getDeviceInfo().executeAsOneOrNull()?.server_device_id
    }

    fun setDeviceName(name: String) {
        val existing = queries.getDeviceInfo().executeAsOneOrNull()
        if (existing != null) {
            queries.updateDeviceName(name)
        } else {
            queries.insertDeviceInfo(
                device_uuid = generateUuid(),
                device_name = name,
                server_device_id = null,
            )
        }
    }

    open suspend fun registerWithServer(): Result<Long> = runCatching {
        val uuid = getDeviceUuid()
        val name = getDeviceName()
        val platform = currentPlatform()

        val device = apiClient.registerDevice(
            RegisterDeviceRequest(
                deviceUuid = uuid,
                name = name.ifEmpty { "$platform device" },
                platform = platform,
            )
        )

        queries.updateServerDeviceId(device.id)
        device.id
    }

    suspend fun updateDeviceNameOnServer(name: String): Result<Unit> = runCatching {
        val serverDeviceId = getServerDeviceId()
            ?: throw IllegalStateException("Device not registered with server")
        apiClient.updateDevice(serverDeviceId, name)
        Unit
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateAndStoreUuid(): String {
        val uuid = generateUuid()
        queries.insertDeviceInfo(
            device_uuid = uuid,
            device_name = "",
            server_device_id = null,
        )
        return uuid
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun generateUuid(): String = Uuid.random().toString()
}
