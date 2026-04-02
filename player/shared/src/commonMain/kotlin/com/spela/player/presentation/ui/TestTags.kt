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

    // Login screen
    const val LOGIN_USERNAME_INPUT = "login_username_input"
    const val LOGIN_PASSWORD_INPUT = "login_password_input"
    const val LOGIN_SUBMIT_BUTTON = "login_submit_button"
    const val LOGIN_REGISTER_TOGGLE = "login_register_toggle"
    const val LOGIN_SERVER_PILL = "login_server_pill"
}
