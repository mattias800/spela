package com.spela.player.domain.model

enum class RenderScale(
    val storageId: String,
    val apiId: String,
    val multiplier: Int,
) {
    NATIVE("native", "native", 1),
    TWO_X("2x", "2x", 2),
    THREE_X("3x", "3x", 3),
    FOUR_X("4x", "4x", 4);

    companion object {
        fun fromStorageId(id: String?): RenderScale? =
            entries.find { it.storageId == id?.trim()?.lowercase() }

        fun fromApiIdOrNull(id: String?): RenderScale? =
            entries.find { it.apiId == id?.trim()?.lowercase() }

        fun fromApiId(id: String?): RenderScale =
            fromApiIdOrNull(id) ?: NATIVE
    }
}

data class CoreVariableOverride(
    val key: String,
    val value: String,
)

fun renderScaleCoreVariables(
    consoleId: String,
    corePath: String,
    scale: RenderScale,
): List<CoreVariableOverride> {
    if (scale == RenderScale.NATIVE || !supportsRenderScale(consoleId)) return emptyList()

    val console = consoleId.normalizedRenderScaleConsoleId()
    val core = corePath.substringAfterLast('/').substringBeforeLast('.').lowercase()

    return when {
        console in setOf("gc", "gcn", "gamecube", "wii") && core.contains("dolphin") ->
            dolphinRenderScale(scale)
        console == "psp" && core.contains("ppsspp") ->
            ppssppRenderScale(scale)
        console in setOf("psx", "ps1", "playstation") && isBeetlePsxHwCore(core) ->
            beetlePsxHwRenderScale(scale)
        console == "n64" && core.contains("mupen64plus") ->
            mupen64PlusRenderScale(scale)
        console == "n64" && core.contains("parallel") ->
            parallelN64RenderScale(scale)
        else -> emptyList()
    }
}

fun supportsRenderScale(consoleId: String): Boolean =
    when (consoleId.normalizedRenderScaleConsoleId()) {
        "gc", "gcn", "gamecube", "wii", "psp", "psx", "ps1", "playstation", "n64" -> true
        else -> false
    }

private fun dolphinRenderScale(scale: RenderScale): List<CoreVariableOverride> =
    when (scale) {
        RenderScale.TWO_X -> listOf(CoreVariableOverride("dolphin_efb_scale", "x2 (1280 x 1056)"))
        RenderScale.THREE_X -> listOf(CoreVariableOverride("dolphin_efb_scale", "x3 (1920 x 1584)"))
        RenderScale.FOUR_X -> listOf(CoreVariableOverride("dolphin_efb_scale", "x4 (2560 x 2112)"))
        RenderScale.NATIVE -> emptyList()
    }

private fun ppssppRenderScale(scale: RenderScale): List<CoreVariableOverride> =
    when (scale) {
        RenderScale.TWO_X -> listOf(CoreVariableOverride("ppsspp_internal_resolution", "960x544"))
        RenderScale.THREE_X -> listOf(CoreVariableOverride("ppsspp_internal_resolution", "1440x816"))
        RenderScale.FOUR_X -> listOf(CoreVariableOverride("ppsspp_internal_resolution", "1920x1088"))
        RenderScale.NATIVE -> emptyList()
    }

private fun beetlePsxHwRenderScale(scale: RenderScale): List<CoreVariableOverride> =
    when (scale) {
        RenderScale.TWO_X -> listOf(CoreVariableOverride("beetle_psx_hw_internal_resolution", "2x"))
        RenderScale.FOUR_X -> listOf(CoreVariableOverride("beetle_psx_hw_internal_resolution", "4x"))
        RenderScale.NATIVE,
        RenderScale.THREE_X -> emptyList()
    }

private fun mupen64PlusRenderScale(scale: RenderScale): List<CoreVariableOverride> =
    listOfNotNull(
        n64FourThreeResolution(scale)?.let { CoreVariableOverride("mupen64plus-43screensize", it) },
        n64SixteenNineResolution(scale)?.let { CoreVariableOverride("mupen64plus-169screensize", it) },
    )

private fun parallelN64RenderScale(scale: RenderScale): List<CoreVariableOverride> =
    n64FourThreeResolution(scale)
        ?.let { listOf(CoreVariableOverride("parallel-n64-screensize", it)) }
        ?: emptyList()

private fun n64FourThreeResolution(scale: RenderScale): String? =
    when (scale) {
        RenderScale.TWO_X -> "640x480"
        RenderScale.THREE_X -> "960x720"
        RenderScale.FOUR_X -> "1280x960"
        RenderScale.NATIVE -> null
    }

private fun n64SixteenNineResolution(scale: RenderScale): String? =
    when (scale) {
        RenderScale.TWO_X -> "640x360"
        RenderScale.THREE_X -> "960x540"
        RenderScale.FOUR_X -> "1280x720"
        RenderScale.NATIVE -> null
    }

private fun isBeetlePsxHwCore(core: String): Boolean =
    core.contains("beetle_psx_hw") || core.contains("mednafen_psx_hw")

private fun String.normalizedRenderScaleConsoleId(): String =
    trim().lowercase().replace("-", "").replace("_", "")
