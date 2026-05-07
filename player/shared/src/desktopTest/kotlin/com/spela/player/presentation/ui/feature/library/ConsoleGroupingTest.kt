package com.spela.player.presentation.ui.feature.library

import com.spela.player.domain.model.Console
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1106 — Kotlin counterpart of `web/src/lib/__tests__/console-grouping.test.ts`.
 * The rule set must match: alphabetical by maker name, "Various" pinned
 * last, releaseYear ascending intra-group, orphan consoles bucketed
 * into Various.
 */
class ConsoleGroupingTest {

    private fun console(
        id: String,
        gen: Int = 0,
        year: Int? = null,
        code: String? = null,
        name: String? = null,
    ) = Console(
        id = id,
        name = id,
        abbreviation = id.uppercase(),
        gameCount = 0,
        generation = gen,
        releaseYear = year,
        makerCode = code,
        makerName = name,
    )

    private val nes = console("nes", gen = 3, year = 1983, code = "nintendo", name = "Nintendo")
    private val snes = console("snes", gen = 4, year = 1990, code = "nintendo", name = "Nintendo")
    private val n64 = console("n64", gen = 5, year = 1996, code = "nintendo", name = "Nintendo")
    private val genesis = console("genesis", gen = 4, year = 1988, code = "sega", name = "Sega")
    private val atari2600 = console("a2600", gen = 2, year = 1977, code = "atari", name = "Atari")
    private val arcade = console("arcade", gen = 101, year = null, code = "various", name = "Various")
    private val scummvm = console("scummvm", gen = 100, year = null, code = "various", name = "Various")

    @Test
    fun groupByGeneration_ordersGenerationsAscending() {
        val groups = groupByGeneration(listOf(n64, nes, atari2600, snes))
        assertEquals(listOf(2, 3, 4, 5), groups.map { it.generation })
    }

    @Test
    fun groupByGeneration_homeComputersAndArcadePinAtEnd() {
        val groups = groupByGeneration(listOf(snes, arcade, atari2600, scummvm))
        assertEquals(listOf(2, 4, 100, 101), groups.map { it.generation })
    }

    @Test
    fun groupByManufacturer_sortsAlphabeticallyByName() {
        val groups = groupByManufacturer(listOf(n64, genesis, nes, atari2600))
        assertEquals(listOf("Atari", "Nintendo", "Sega"), groups.map { it.makerName })
    }

    @Test
    fun groupByManufacturer_pinsVariousLast() {
        // Atari < Nintendo < Sega < V alphabetically would put Various
        // between Sega and ... well, after S. But the rule is Various
        // pinned LAST, regardless of letter — so even if there's a maker
        // called "Yamaha" in the list, Various still ends up after it.
        val groups = groupByManufacturer(listOf(arcade, genesis, nes, atari2600, scummvm))
        assertEquals(
            listOf("Atari", "Nintendo", "Sega", "Various"),
            groups.map { it.makerName },
        )
    }

    @Test
    fun groupByManufacturer_ordersConsolesByReleaseYearAscending() {
        // NES 1983 → SNES 1990 → N64 1996 inside Nintendo.
        val groups = groupByManufacturer(listOf(n64, snes, nes))
        assertEquals("Nintendo", groups[0].makerName)
        assertEquals(listOf("nes", "snes", "n64"), groups[0].consoles.map { it.id })
    }

    @Test
    fun groupByManufacturer_bucketsOrphansIntoVarious() {
        // Console rows from before the maker column was added carry
        // null/empty makerCode. They should land in Various rather than
        // be silently dropped — keeps grouping idempotent across data
        // states.
        val orphan = console("orphan", code = null, name = null)
        val groups = groupByManufacturer(listOf(nes, orphan))
        val allIds = groups.flatMap { it.consoles.map { c -> c.id } }
        assertTrue("orphan" in allIds, "orphan must survive grouping")
        assertTrue("nes" in allIds, "nes must survive grouping")
        // Nintendo is the only named manufacturer; orphan goes to
        // Various; ordering: Nintendo, Various.
        assertEquals(listOf("Nintendo", "Various"), groups.map { it.makerName })
    }

    @Test
    fun groupByManufacturer_returnsEmptyForEmptyInput() {
        assertEquals(emptyList(), groupByManufacturer(emptyList()))
    }

    @Test
    fun groupConsoles_dispatchesByGroupingArg() {
        assertTrue(
            groupConsoles(listOf(nes, atari2600), ConsoleGrouping.Generation)
                .all { it is ConsoleGroupSection.Generation },
        )
        assertTrue(
            groupConsoles(listOf(nes, atari2600), ConsoleGrouping.Manufacturer)
                .all { it is ConsoleGroupSection.Manufacturer },
        )
    }
}
