package com.spela.player.domain.model

enum class WidescreenMode(
    val storageId: String,
    val label: String,
    val optionLabel: String,
    val description: String,
    val nativeId: Int,
) {
    NATIVE(
        storageId = "native",
        label = "Auto",
        optionLabel = "Auto",
        description = "Use the core's reported display size",
        nativeId = 0,
    ),
    FOUR_THREE(
        storageId = "4_3",
        label = "4:3",
        optionLabel = "4:3 (Normal)",
        description = "Preserve the standard 4:3 frame",
        nativeId = 1,
    ),
    STRETCH(
        storageId = "stretch",
        label = "Stretch",
        optionLabel = "Stretch (Full)",
        description = "Fill widescreen displays without side bars",
        nativeId = 2,
    ),
    ZOOM(
        storageId = "zoom",
        label = "Zoom",
        optionLabel = "Zoom",
        description = "Larger 4:3 with slight crop and reduced bars",
        nativeId = 3,
    );

    val displayAspectRatio: Float
        get() = when (this) {
            NATIVE -> 0f
            FOUR_THREE, ZOOM -> 4f / 3f
            STRETCH -> 16f / 9f
        }

    companion object {
        val selectableModes: List<WidescreenMode> = listOf(FOUR_THREE, STRETCH, ZOOM)

        fun fromStorageId(id: String?): WidescreenMode? =
            entries.find { it.storageId == id }
    }
}

fun defaultWidescreenMode(consoleId: String): WidescreenMode =
    when (consoleId.normalizedWidescreenConsoleId()) {
        "wii" -> WidescreenMode.STRETCH
        "gc", "gcn", "gamecube", "ps2" -> WidescreenMode.FOUR_THREE
        else -> WidescreenMode.NATIVE
    }

fun supportsWidescreenMode(consoleId: String): Boolean =
    when (consoleId.normalizedWidescreenConsoleId()) {
        "wii", "gc", "gcn", "gamecube", "ps2" -> true
        else -> false
    }

private fun String.normalizedWidescreenConsoleId(): String =
    trim().lowercase().replace("-", "").replace("_", "")
