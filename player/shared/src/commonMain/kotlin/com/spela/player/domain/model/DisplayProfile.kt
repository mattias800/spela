package com.spela.player.domain.model

enum class DisplayAspectChoice(
    val storageId: String,
    val label: String,
    val optionLabel: String,
    val description: String,
) {
    AUTO(
        storageId = "auto",
        label = "Auto",
        optionLabel = "Auto",
        description = "Choose the best presentation for this game",
    ),
    ORIGINAL(
        storageId = "original",
        label = "Original",
        optionLabel = "Original",
        description = "Preserve the game's original frame",
    ),
    SIXTEEN_NINE(
        storageId = "16_9",
        label = "16:9",
        optionLabel = "16:9",
        description = "Use anamorphic widescreen presentation",
    ),
    ZOOM(
        storageId = "zoom",
        label = "Zoom",
        optionLabel = "Zoom",
        description = "Larger 4:3 with slight crop and reduced bars",
    );

    companion object {
        val selectableChoices: List<DisplayAspectChoice> = listOf(AUTO, ORIGINAL, SIXTEEN_NINE, ZOOM)

        fun fromStorageId(id: String?): DisplayAspectChoice? =
            when (id?.trim()?.lowercase()) {
                AUTO.storageId, "native" -> AUTO
                ORIGINAL.storageId, "4_3" -> ORIGINAL
                SIXTEEN_NINE.storageId, "stretch" -> SIXTEEN_NINE
                ZOOM.storageId -> ZOOM
                else -> null
            }
    }
}

enum class RenderScaleChoice(
    val storageId: String,
    val label: String,
    val optionLabel: String,
    val description: String,
    val scale: RenderScale?,
) {
    AUTO(
        storageId = "auto",
        label = "Auto",
        optionLabel = "Auto",
        description = "Choose the best internal resolution for this core",
        scale = null,
    ),
    NATIVE(
        storageId = "native",
        label = "Native",
        optionLabel = "Native",
        description = "Use the core's default internal resolution",
        scale = RenderScale.NATIVE,
    ),
    TWO_X(
        storageId = "2x",
        label = "2x",
        optionLabel = "2x",
        description = "Render at twice the original internal resolution",
        scale = RenderScale.TWO_X,
    ),
    THREE_X(
        storageId = "3x",
        label = "3x",
        optionLabel = "3x",
        description = "Render at three times the original internal resolution",
        scale = RenderScale.THREE_X,
    ),
    FOUR_X(
        storageId = "4x",
        label = "4x",
        optionLabel = "4x",
        description = "Render at four times the original internal resolution",
        scale = RenderScale.FOUR_X,
    );

    companion object {
        fun fromStorageId(id: String?): RenderScaleChoice? =
            entries.find { it.storageId == id?.trim()?.lowercase() }

        fun fromRenderScale(scale: RenderScale): RenderScaleChoice =
            when (scale) {
                RenderScale.NATIVE -> NATIVE
                RenderScale.TWO_X -> TWO_X
                RenderScale.THREE_X -> THREE_X
                RenderScale.FOUR_X -> FOUR_X
            }
    }
}

data class ResolvedDisplayProfile(
    val aspectChoice: DisplayAspectChoice,
    val aspectMode: WidescreenMode,
    val aspectLabel: String,
    val aspectStateDescription: String,
    val renderScaleChoice: RenderScaleChoice,
    val renderScale: RenderScale,
    val renderScaleLabel: String,
    val renderScaleStateDescription: String,
)

data class DisplayGameProfile(
    val gameIds: Set<String> = emptySet(),
    val consoleIds: Set<String>,
    val titleContains: List<String>,
    val aspectChoice: DisplayAspectChoice,
)

object DisplayProfileResolver {
    private val curatedProfiles = listOf(
        DisplayGameProfile(
            consoleIds = setOf("wii"),
            titleContains = listOf("zelda", "twilight", "princess"),
            aspectChoice = DisplayAspectChoice.SIXTEEN_NINE,
        ),
    )

    fun resolve(
        gameId: String,
        gameTitle: String,
        consoleId: String,
        corePath: String,
        aspectChoice: DisplayAspectChoice = DisplayAspectChoice.AUTO,
        renderScaleChoice: RenderScaleChoice = RenderScaleChoice.AUTO,
        platform: String = "",
        profiles: List<DisplayGameProfile> = curatedProfiles,
    ): ResolvedDisplayProfile {
        val resolvedAspectChoice = resolveAutomaticAspectChoice(
            gameId = gameId,
            consoleId = consoleId,
            gameTitle = gameTitle,
            profiles = profiles,
        )
        val effectiveAspectChoice = if (aspectChoice == DisplayAspectChoice.AUTO) {
            resolvedAspectChoice
        } else {
            aspectChoice
        }
        val aspectMode = effectiveAspectChoice.toWidescreenMode(consoleId)

        val resolvedScale = automaticRenderScale(
            consoleId = consoleId,
            corePath = corePath,
            platform = platform,
        )
        val requestedScale = renderScaleChoice.scale ?: resolvedScale
        val effectiveScale = supportedRenderScaleOrNative(
            consoleId = consoleId,
            corePath = corePath,
            scale = requestedScale,
        )

        return ResolvedDisplayProfile(
            aspectChoice = aspectChoice,
            aspectMode = aspectMode,
            aspectLabel = resolvedAspectLabel(aspectChoice, effectiveAspectChoice),
            aspectStateDescription = resolvedAspectStateDescription(aspectChoice, effectiveAspectChoice),
            renderScaleChoice = renderScaleChoice,
            renderScale = effectiveScale,
            renderScaleLabel = resolvedRenderScaleLabel(renderScaleChoice, effectiveScale),
            renderScaleStateDescription = resolvedRenderScaleStateDescription(renderScaleChoice, effectiveScale),
        )
    }

    fun resolveAspect(
        gameId: String,
        gameTitle: String,
        consoleId: String,
        aspectChoice: DisplayAspectChoice,
        profiles: List<DisplayGameProfile> = curatedProfiles,
    ): Pair<WidescreenMode, String> {
        val effectiveChoice = if (aspectChoice == DisplayAspectChoice.AUTO) {
            resolveAutomaticAspectChoice(
                gameId = gameId,
                consoleId = consoleId,
                gameTitle = gameTitle,
                profiles = profiles,
            )
        } else {
            aspectChoice
        }
        return effectiveChoice.toWidescreenMode(consoleId) to resolvedAspectLabel(aspectChoice, effectiveChoice)
    }

    private fun resolveAutomaticAspectChoice(
        gameId: String,
        consoleId: String,
        gameTitle: String,
        profiles: List<DisplayGameProfile>,
    ): DisplayAspectChoice =
        profiles.firstOrNull { it.matches(gameId, consoleId, gameTitle) }?.aspectChoice
            ?: DisplayAspectChoice.ORIGINAL

    private fun DisplayGameProfile.matches(gameId: String, consoleId: String, gameTitle: String): Boolean {
        if (gameIds.isNotEmpty() && gameId in gameIds) return true
        val normalizedConsoleId = consoleId.normalizedDisplayProfileId()
        if (consoleIds.none { it.normalizedDisplayProfileId() == normalizedConsoleId }) return false
        val normalizedTitle = gameTitle.normalizedDisplayProfileTitle()
        return titleContains.all { normalizedTitle.contains(it.normalizedDisplayProfileTitle()) }
    }

    private fun DisplayAspectChoice.toWidescreenMode(consoleId: String): WidescreenMode =
        when (this) {
            DisplayAspectChoice.AUTO -> error("Auto must be resolved before mapping to a renderer mode")
            DisplayAspectChoice.ORIGINAL -> if (supportsWidescreenMode(consoleId)) {
                WidescreenMode.FOUR_THREE
            } else {
                WidescreenMode.NATIVE
            }
            DisplayAspectChoice.SIXTEEN_NINE -> WidescreenMode.STRETCH
            DisplayAspectChoice.ZOOM -> WidescreenMode.ZOOM
        }

    private fun automaticRenderScale(
        consoleId: String,
        corePath: String,
        platform: String,
    ): RenderScale {
        val console = consoleId.normalizedDisplayProfileId()
        val core = corePath.substringAfterLast('/').substringBeforeLast('.').lowercase()
        return when {
            console in setOf("gc", "gcn", "gamecube", "wii") && core.contains("dolphin") ->
                RenderScale.TWO_X
            console == "psp" && core.contains("ppsspp") ->
                RenderScale.FOUR_X
            console in setOf("psx", "ps1", "playstation") &&
                (core.contains("beetle_psx_hw") || core.contains("mednafen_psx_hw")) ->
                RenderScale.FOUR_X
            console == "n64" && platform == "macos" ->
                RenderScale.NATIVE
            console == "n64" && (core.contains("mupen64plus") || core.contains("parallel")) ->
                RenderScale.THREE_X
            else -> RenderScale.NATIVE
        }
    }

    private fun supportedRenderScaleOrNative(
        consoleId: String,
        corePath: String,
        scale: RenderScale,
    ): RenderScale =
        if (scale == RenderScale.NATIVE || renderScaleCoreVariables(consoleId, corePath, scale).isNotEmpty()) {
            scale
        } else {
            RenderScale.NATIVE
        }

    private fun resolvedAspectLabel(
        selected: DisplayAspectChoice,
        effective: DisplayAspectChoice,
    ): String =
        if (selected == DisplayAspectChoice.AUTO) {
            "Auto (${effective.label})"
        } else {
            selected.label
        }

    private fun resolvedAspectStateDescription(
        selected: DisplayAspectChoice,
        effective: DisplayAspectChoice,
    ): String =
        if (selected == DisplayAspectChoice.AUTO) {
            "Auto, resolved to ${effective.optionLabel}"
        } else {
            selected.optionLabel
        }

    private fun resolvedRenderScaleLabel(
        selected: RenderScaleChoice,
        effective: RenderScale,
    ): String =
        when {
            selected == RenderScaleChoice.AUTO -> "Auto (${effective.displayLabel})"
            selected.scale != null && selected.scale != effective -> effective.displayLabel
            else -> selected.label
        }

    private fun resolvedRenderScaleStateDescription(
        selected: RenderScaleChoice,
        effective: RenderScale,
    ): String =
        when {
            selected == RenderScaleChoice.AUTO -> "Auto, resolved to ${effective.displayLabel}"
            selected.scale != null && selected.scale != effective ->
                "${selected.optionLabel} unavailable, using ${effective.displayLabel}"
            else -> selected.optionLabel
        }

    private val RenderScale.displayLabel: String
        get() = when (this) {
            RenderScale.NATIVE -> "Native"
            RenderScale.TWO_X -> "2x"
            RenderScale.THREE_X -> "3x"
            RenderScale.FOUR_X -> "4x"
        }

    private fun String.normalizedDisplayProfileId(): String =
        trim().lowercase().replace("-", "").replace("_", "")

    private fun String.normalizedDisplayProfileTitle(): String =
        trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
