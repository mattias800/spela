package com.spela.player.presentation.ui.screen

import com.spela.player.domain.model.Console

/**
 * Ordering for flat console lists (e.g. the Per-Console settings list):
 * alphabetical by name, case-insensitive.
 *
 * Note: the main Consoles library screen intentionally groups consoles by
 * generation/manufacturer and is not affected by this.
 */
internal fun List<Console>.sortedForConsoleList(): List<Console> =
    sortedBy { it.name.lowercase() }
