package com.spela.player.presentation.ui.screen

import com.spela.player.domain.model.Console
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleListSortTest {

    private fun console(name: String) =
        Console(id = name.lowercase(), name = name, abbreviation = name.take(3), gameCount = 0)

    @Test
    fun sortsAlphabeticallyCaseInsensitive() {
        val input = listOf(
            console("Super Nintendo"),
            console("atari 2600"),
            console("Nintendo 64"),
            console("Dreamcast"),
        )
        val sorted = input.sortedForConsoleList().map { it.name }
        assertEquals(
            listOf("atari 2600", "Dreamcast", "Nintendo 64", "Super Nintendo"),
            sorted,
        )
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(0, emptyList<Console>().sortedForConsoleList().size)
    }
}
