package com.spela.player.presentation.ui

/**
 * Shared test tag constants for UI components.
 *
 * Used by both the composables (via Modifier.testTag) and the test suites
 * (via onNodeWithTag). Centralizing them here ensures tests don't break
 * when display text changes.
 */
object TestTags {
    // Screens
    const val SCREEN_SERVER_CONNECTION = "screen_server_connection"
    const val SCREEN_LOGIN = "screen_login"
    const val SCREEN_HOME = "screen_home"

    // Server connection screen
    const val SERVER_LIST = "server_list"
    const val SERVER_ADD_FORM = "server_add_form"
    const val SERVER_NAME_INPUT = "server_name_input"
    const val SERVER_URL_INPUT = "server_url_input"
    const val SERVER_CONNECT_BUTTON = "server_connect_button"
    const val SERVER_ADD_BUTTON = "server_add_button"
    const val SERVER_ADD_TOGGLE_BUTTON = "server_add_toggle_button"

    // Console screen
    const val SCREEN_CONSOLE = "screen_console"

    // Login screen
    const val LOGIN_USERNAME_INPUT = "login_username_input"
    const val LOGIN_PASSWORD_INPUT = "login_password_input"
    const val LOGIN_SUBMIT_BUTTON = "login_submit_button"
    const val LOGIN_REGISTER_TOGGLE = "login_register_toggle"
    const val LOGIN_SERVER_PILL = "login_server_pill"

    // Common controls — rendered by shared components, appear on every
    // screen that uses them. Tests should prefer these over text /
    // contentDescription matching so a copy change doesn't break the
    // suite. Add a new TAG constant here (not a per-screen constant) any
    // time you introduce a UI element that shows up in more than one
    // place.
    const val BACK_BUTTON = "back_button"              // SpTopBar arrow
    const val NAV_HOME = "nav_home"                     // bottom nav + rail
    const val NAV_EXPLORE = "nav_explore"
    const val NAV_CONSOLES = "nav_consoles"
    const val NAV_COLLECTIONS = "nav_collections"
    const val NAV_ACTIVITY = "nav_activity"
    const val NAV_CHALLENGES = "nav_challenges"
    const val NAV_NETPLAY = "nav_netplay"
    const val NAV_SETTINGS = "nav_settings"

    // Settings screen — left-side category list on the list-detail layout.
    // Mirrors the [SettingsCategory] enum one-to-one.
    const val SETTINGS_CATEGORY_GENERAL = "settings_category_general"
    const val SETTINGS_CATEGORY_EMULATION = "settings_category_emulation"
    const val SETTINGS_CATEGORY_CONTROLS = "settings_category_controls"
    const val SETTINGS_CATEGORY_CONSOLES = "settings_category_consoles"
    const val SETTINGS_CATEGORY_ACHIEVEMENTS = "settings_category_achievements"
    const val SETTINGS_CATEGORY_STORAGE_SYNC = "settings_category_storage_sync"
    const val SETTINGS_CATEGORY_ABOUT = "settings_category_about"

    // Console library — per-console card on the Consoles screen
    fun consoleCard(consoleId: String) = "console_card_$consoleId"
    fun consoleBrowseGames(consoleId: String) = "console_browse_games_$consoleId"

    // Game detail — primary CTA changes label between Play / Resume / Download
    // depending on save + cache state. Tests should target the tag, not the
    // visible label, since a Continue/Resume copy change shouldn't fail tests.
    const val GAME_DETAIL_PLAY_BUTTON = "game_detail_play_button"
    const val GAME_DETAIL_DOWNLOAD_BUTTON = "game_detail_download_button"
}
