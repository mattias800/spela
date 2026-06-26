package com.spela.player.presentation.ui.gamepad

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * Tracks the gamepad-focusable elements inside a single
 * [androidx.compose.foundation.verticalScroll] viewport
 * ([com.spela.player.presentation.ui.components.SpScrollableContent]) so that
 * a d-pad press can re-acquire focus to a *visible* element when the currently
 * focused element has been scrolled out of view.
 *
 * ## Why this exists (#1194 / #1452)
 *
 * `SpScrollableContent` is a plain `Column` + `verticalScroll`, so every
 * focusable child stays composed regardless of scroll position. After a
 * manual scroll (right stick #1362, touch, or `performScrollTo`) the focused
 * element can be far above the viewport. The next d-pad press then runs
 * `focusManager.moveFocus(direction)` from that off-screen element, lands on
 * its (also off-screen) neighbour, and [Modifier.centerOnFocus] /
 * `focusable()`'s `bringIntoView` yank the list back toward the top — the
 * exact "scroll jumps back to the offscreen focused element" symptom #1194
 * set out to kill. A `LazyColumn` avoids this by disposing off-screen items
 * so `moveFocus(Next)` only finds visible ones; a `verticalScroll` Column has
 * no such notion, so we reconstruct it here.
 *
 * Each [centerOnFocus] element registers its live root-Y bounds, focus state,
 * and [FocusRequester]. `SpScrollableContent` keeps [viewportTopInRoot] /
 * [viewportHeight] up to date and, on a directional key, calls
 * [redirectIfFocusedOffscreen] *before* normal navigation runs.
 *
 * Single-threaded by construction: registration happens during composition and
 * [redirectIfFocusedOffscreen] runs from the key-event handler — both on the
 * main thread — so a plain list needs no synchronization.
 */
class ScrollFocusRegistry {
    /** A registered focusable. Positions are read live via accessors so the
     *  registry never holds stale coordinates. */
    class Entry(
        val requester: FocusRequester,
        val topInRoot: () -> Float,
        val height: () -> Float,
        val isFocused: () -> Boolean,
    )

    private val entries = ArrayList<Entry>()

    /** Top edge of the scroll viewport in root coordinates. */
    var viewportTopInRoot: Float = 0f

    /** Height of the scroll viewport in pixels. */
    var viewportHeight: Float = 0f

    fun register(entry: Entry) {
        entries.add(entry)
    }

    fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    private fun Entry.intersectsViewport(): Boolean {
        if (viewportHeight <= 0f) return false
        val top = topInRoot()
        val bottom = top + height()
        return bottom > viewportTopInRoot && top < viewportTopInRoot + viewportHeight
    }

    /**
     * If a focusable is currently focused but scrolled out of the viewport,
     * move focus to the first visible focusable instead and return `true`
     * (the caller should consume the key so normal directional navigation —
     * which would spatially-move from the off-screen element and snap-scroll
     * back — does not run). Returns `false` in every other case so normal
     * navigation is completely unaffected when the focused element is visible.
     */
    fun redirectIfFocusedOffscreen(): Boolean {
        val focused = entries.firstOrNull { it.isFocused() } ?: return false
        if (focused.intersectsViewport()) return false

        // Re-acquire focus to the topmost focusable whose top edge is *within*
        // the viewport — i.e. a card that genuinely starts on screen, not one
        // bleeding in from above. Picking a partly-above card would let
        // centerOnFocus pull it toward centre and scroll the list back up,
        // which is the very snap we're preventing.
        val target = entries
            .filter {
                val top = it.topInRoot()
                top >= viewportTopInRoot && top < viewportTopInRoot + viewportHeight
            }
            .minByOrNull { it.topInRoot() }
            ?: return false

        return try {
            target.requester.requestFocus()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Provided by [com.spela.player.presentation.ui.components.SpScrollableContent].
 * Null outside a `verticalScroll` viewport (e.g. inside a `LazyColumn`, where
 * off-screen-item disposal already gives the same behavior), in which case
 * [centerOnFocus] registration is a no-op.
 */
val LocalScrollFocusRegistry = compositionLocalOf<ScrollFocusRegistry?> { null }
