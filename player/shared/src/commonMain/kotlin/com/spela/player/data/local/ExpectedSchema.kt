package com.spela.player.data.local

/**
 * Authoritative list of tables and columns expected by the current SQLDelight schema.
 * Used at startup to validate an existing database before opening it.
 *
 * Keep this in sync with SpelaDatabase.sq — when you add a table or column there,
 * add it here too. The desktop and Android tests will catch any drift.
 */
object ExpectedSchema {
    val tables: Map<String, Set<String>> = mapOf(
        "ServerConnectionEntity" to setOf("id", "name", "url", "is_active"),
        "DownloadEntity" to setOf("game_id", "local_path", "file_size", "downloaded_at"),
        "AuthTokenEntity" to setOf("id", "access_token", "refresh_token", "expires_at"),
        "PlayHistoryEntity" to setOf("id", "game_id", "started_at", "duration_seconds"),
        "ShaderOverrideEntity" to setOf("console_id", "shader"),
        "KeyMappingEntity" to setOf("id", "console_id", "port", "platform_key_code", "libretro_button_id"),
        "GameKeyMappingEntity" to setOf("id", "game_id", "libretro_button_id", "platform_key_code"),
        "DeviceInfoEntity" to setOf("id", "device_uuid", "device_name", "server_device_id"),
        "CachedPreferencesEntity" to setOf("id", "json_data", "updated_at"),
        "CachedConsoleEntity" to setOf(
            "id", "name", "abbreviation", "game_count", "color_theme",
            "cover_aspect_ratio", "default_core", "icon_url", "logo_url", "save_state_support",
        ),
        "CachedGameEntity" to setOf(
            "id", "console_id", "title", "console_name", "cover_url", "description",
            "developer", "publisher", "release_date", "genre", "file_size", "file_name",
            "cover_aspect_ratio", "disc_count", "is_favorite", "is_in_play_later",
            "last_played_at", "total_play_time", "cached_at",
        ),
        "DeviceSettingEntity" to setOf("key", "value"),
        "CheatEntity" to setOf("id", "game_id", "cheat_index", "description", "code", "enabled", "cached_at"),
        "PendingSaveUploadEntity" to setOf(
            "id", "session_id", "kind", "slot", "name", "core_name", "compression",
            "file_path", "file_size", "screenshot_path", "created_at",
            "retry_count", "last_error",
        ),
        "OnboardingHintEntity" to setOf("hint_key", "dismissed_at"),
    )
}
