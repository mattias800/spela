package com.spela.player.presentation.ui.feature.library

import com.spela.player.domain.model.Console

/**
 * The two grouping modes the consoles list supports. Mirrors the
 * web side's `ConsoleGrouping` (`web/src/lib/console-grouping.ts`).
 */
enum class ConsoleGrouping { Generation, Manufacturer }

/**
 * One section in the rendered consoles list. The `kind` discriminator
 * keeps the [ConsolesGrid] composable type-safe — generation-only
 * fields (`years`, the "Nth Generation" labelling) live on
 * [ConsoleGroupSection.Generation], maker-only fields live on
 * [ConsoleGroupSection.Manufacturer].
 */
sealed class ConsoleGroupSection {
    abstract val key: String
    abstract val title: String
    abstract val consoles: List<Console>

    data class Generation(
        val generation: Int,
        override val title: String,
        override val consoles: List<Console>,
    ) : ConsoleGroupSection() {
        override val key: String get() = "gen-$generation"
    }

    data class Manufacturer(
        val makerCode: String,
        val makerName: String,
        override val consoles: List<Console>,
    ) : ConsoleGroupSection() {
        override val key: String get() = "maker-$makerCode"
        override val title: String get() = makerName
    }
}

internal const val VARIOUS_MAKER_CODE = "various"

private fun generationLabelInternal(generation: Int): String = when (generation) {
    2 -> "2nd Generation · 1976–1992"
    3 -> "3rd Generation · 1983–1992"
    4 -> "4th Generation · 1987–1996"
    5 -> "5th Generation · 1993–2006"
    6 -> "6th Generation · 1998–2007"
    7 -> "7th Generation · 2004–2013"
    8 -> "8th Generation · 2011–2020"
    9 -> "9th Generation · 2017–present"
    100 -> "Home Computers · 1977–1995"
    101 -> "Arcade · 1971–present"
    else -> "Other"
}

fun groupByGeneration(consoles: List<Console>): List<ConsoleGroupSection.Generation> =
    consoles
        .groupBy { it.generation }
        .toSortedMap()
        .map { (gen, list) ->
            ConsoleGroupSection.Generation(
                generation = gen,
                title = generationLabelInternal(gen),
                consoles = list,
            )
        }

/**
 * Groups consoles by `makerCode`. Manufacturers are sorted alphabetically
 * by `makerName` so users can predict where each row lives. The catch-all
 * `"various"` bucket (arcade, DOS demos, Amiga demos, ScummVM — see
 * `seed_metadata.go`) is pinned last regardless of letter.
 *
 * Within each manufacturer, consoles are ordered by [Console.releaseYear]
 * ascending — same chronological intra-group order the generation view
 * uses. Consoles missing `makerCode` (rare DB orphans pre-dating the
 * maker column) bucket into `Various` rather than disappearing.
 */
fun groupByManufacturer(consoles: List<Console>): List<ConsoleGroupSection.Manufacturer> {
    val buckets = linkedMapOf<String, MutableList<Console>>()
    val displayName = mutableMapOf<String, String>()
    for (c in consoles) {
        val code = c.makerCode?.takeIf { it.isNotBlank() } ?: VARIOUS_MAKER_CODE
        val name = c.makerName?.takeIf { it.isNotBlank() } ?: "Various"
        buckets.getOrPut(code) { mutableListOf() }.add(c)
        // First occurrence wins — keeps the bucket label stable even
        // if a later orphan for the same code carries an empty name.
        if (code !in displayName) {
            displayName[code] = name
        }
    }
    return buckets.entries
        .map { (code, list) ->
            list.sortBy { it.releaseYear ?: 0 }
            ConsoleGroupSection.Manufacturer(
                makerCode = code,
                makerName = displayName[code] ?: "Various",
                consoles = list,
            )
        }
        .sortedWith(
            compareBy(
                // Pin "various" to the end regardless of name.
                { if (it.makerCode == VARIOUS_MAKER_CODE) 1 else 0 },
                { it.makerName.lowercase() },
            ),
        )
}

fun groupConsoles(
    consoles: List<Console>,
    grouping: ConsoleGrouping,
): List<ConsoleGroupSection> = when (grouping) {
    ConsoleGrouping.Generation -> groupByGeneration(consoles)
    ConsoleGrouping.Manufacturer -> groupByManufacturer(consoles)
}
