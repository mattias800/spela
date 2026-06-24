package com.spela.player.data

import com.spela.client.models.ConsoleResponse
import com.spela.client.models.HardwareMakerResponse
import com.spela.client.models.MediaTypeCategoryResponse
import com.spela.client.models.MediaTypeResponse
import com.spela.player.data.remote.dto.toDomain
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Locks the console hardware-photo field flowing from the API response into the
 * domain model (#1441), including the null case (consoles with no bundled photo
 * → null, so the UI falls back to the logo/watermark).
 */
class ConsoleMapperTest {

    private fun response(photoUrl: String?) = ConsoleResponse(
        abbreviation = "NES",
        browserPlayable = false,
        code = "nes",
        colorTheme = "#e53e3e",
        coverAspectRatio = 0.75,
        createdAt = Instant.fromEpochSeconds(0),
        defaultCore = "nestopia",
        emulatorJsCore = "",
        extensions = emptyList(),
        gameCount = 3,
        generation = 3,
        iconUrl = "/api/consoles/nes/icon",
        id = "nes",
        logoAspectRatio = null,
        logoPngUrl = "/api/consoles/nes/logo.png",
        logoUrl = "/api/consoles/nes/logo",
        maker = HardwareMakerResponse(code = "nintendo", name = "Nintendo"),
        mediaType = MediaTypeResponse(
            category = MediaTypeCategoryResponse(code = "cartridge", name = "Cartridge"),
            code = "cartridge",
            name = "ROM Cartridge",
        ),
        name = "Nintendo Entertainment System",
        photoUrl = photoUrl,
        playable = true,
        releaseYear = 1983,
        saveStatePolicy = "small",
        saveStateSupport = true,
        summary = null,
        unitsSold = null,
        updatedAt = Instant.fromEpochSeconds(0),
    )

    @Test
    fun mapsPhotoUrl() {
        assertEquals("/api/consoles/nes/photo", response("/api/consoles/nes/photo").toDomain().photoUrl)
    }

    @Test
    fun nullPhotoUrlMapsToNull() {
        assertNull(response(null).toDomain().photoUrl)
    }
}
