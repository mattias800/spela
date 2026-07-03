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
    const val NAV_CONNECTED_SERVERS = "nav_connected_servers"
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
    const val SETTINGS_SAVE_SYNC_QUEUE_SUMMARY = "settings_save_sync_queue_summary"
    const val SETTINGS_SAVE_SYNC_QUEUE_EMPTY = "settings_save_sync_queue_empty"
    fun settingsSaveSyncJob(jobId: Long) = "settings_save_sync_job_$jobId"
    const val SETTINGS_PLAY_TIME_SYNC_QUEUE_SUMMARY = "settings_play_time_sync_queue_summary"
    const val SETTINGS_PLAY_TIME_SYNC_QUEUE_EMPTY = "settings_play_time_sync_queue_empty"
    fun settingsPlayTimeSyncJob(jobId: Long) = "settings_play_time_sync_job_$jobId"

    // Console library — per-console card on the Consoles screen
    fun consoleCard(consoleId: String) = "console_card_$consoleId"
    fun consoleCardPhoto(consoleId: String) = "console_card_photo_$consoleId"
    fun consoleCardLogo(consoleId: String) = "console_card_logo_$consoleId"
    fun consoleBrowseGames(consoleId: String) = "console_browse_games_$consoleId"

    // Console detail — terminal browse section and admin overflow menu
    const val CONSOLE_BROWSE_ALL_SECTION = "console_browse_all_section"
    const val CONSOLE_BROWSE_ALL_CTA = "console_browse_all_cta"
    const val CONSOLE_ADMIN_MENU_BUTTON = "console_admin_menu_button"
    const val CONSOLE_ADMIN_MENU_SETTINGS = "console_admin_menu_settings"
    const val CONSOLE_SETTINGS_BUTTON = "console_settings_button"

    // Game detail — primary CTA changes label between Play / Resume / Download
    // depending on save + cache state. Tests should target the tag, not the
    // visible label, since a Continue/Resume copy change shouldn't fail tests.
    const val GAME_DETAIL_PLAY_BUTTON = "game_detail_play_button"
    const val GAME_DETAIL_DOWNLOAD_BUTTON = "game_detail_download_button"

    // Game detail — More-actions overflow menu (GameActionsMenu). The
    // dropdown items moved here from being top-level buttons; tests
    // should drive them by tag rather than text labels which are
    // user-facing copy and may be localised or renamed.
    const val GAME_DETAIL_MORE_ACTIONS = "game_detail_more_actions"
    const val GAME_DETAIL_MENU_FAVORITE = "game_detail_menu_favorite"
    const val GAME_DETAIL_MENU_PLAY_LATER = "game_detail_menu_play_later"
    const val GAME_DETAIL_MENU_ADD_TO_COLLECTION = "game_detail_menu_add_to_collection"

    // Collections screen — list-detail with two section headers in a
    // single LazyColumn. The empty state shares the screen container.
    const val SCREEN_COLLECTIONS = "screen_collections"
    const val COLLECTIONS_LIST = "collections_list"
    const val COLLECTIONS_MY_HEADER = "collections_my_header"
    const val COLLECTIONS_PUBLIC_HEADER = "collections_public_header"
    const val COLLECTIONS_EMPTY_STATE = "collections_empty_state"
    const val COLLECTIONS_FAB = "collections_fab"

    // Collection detail — owner-only edit / delete buttons in the top bar.
    const val COLLECTION_DETAIL_EDIT = "collection_detail_edit"
    const val COLLECTION_DETAIL_DELETE = "collection_detail_delete"

    // Standardised gamepad-mode screen heading (SpScreenHeading, #1529).
    // Touch mode shows SpTopBar with the same title, so tests distinguish
    // the gamepad heading by tag rather than text.
    const val SCREEN_HEADING = "screen_heading"
}
