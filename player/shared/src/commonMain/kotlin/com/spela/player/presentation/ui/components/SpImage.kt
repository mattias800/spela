package com.spela.player.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.presentation.ui.theme.SpColor
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Default upper bound for the random per-request stagger, in milliseconds.
 *
 * On screens like Explore the eager outer column composes ~19 sections at
 * first paint, and (until SpCarousel goes lazy) every card in every shelf
 * — easily 100+ images simultaneously. Without this, every image kicks
 * off its Coil request on frame 0 and the main-thread dispatcher gets
 * swamped. With a uniform 0..N spread, requests ramp instead of pile.
 *
 * 1000 ms is the sweet spot from #1168 — most disk-cached images snap in
 * imperceptibly after the stagger, cold-cache loads behave the same as
 * before but the cluster is smoothed.
 */
private const val DEFAULT_STAGGER_MS: Long = 1000L

/**
 * DESIGN component — Spela's shared async image primitive.
 *
 * Every image-bearing surface in the app routes through this composable
 * so we can apply load-shaping policies (request stagger, placeholder /
 * error UX, crossfade) in exactly one place. Higher-level components
 * (SpCoverArt, SpAreaSizedImage, SpAvatar, SpHeroBanner, …) contribute
 * their own size / shape / clipping and delegate the image work here.
 *
 * Behaviour:
 *
 * - **Random request stagger.** Picks a uniform delay in [0, [staggerMs])
 *   the first time a non-null [model] is observed (per call site). While
 *   the timer is still running the [placeholder] slot is shown; the
 *   Coil request only fires once the timer elapses. This smooths the
 *   request burst that would otherwise hit the dispatcher pool on frame
 *   zero of screens that compose many images at once (#1168). Cached
 *   images still feel near-instant — the stagger just delays the Coil
 *   call, not the decode, and Coil's memory-cache short-circuit makes
 *   already-decoded bitmaps render in the same frame the stagger ends.
 *   Pass `staggerMs = 0L` to opt out for screens (e.g. lightboxes,
 *   single-shot details) where stalling is worse than bursting.
 * - **Null model = placeholder.** No Coil request, no timer, just the
 *   placeholder slot.
 * - **Default placeholder / error.** A static SurfaceBright-tinted Box.
 *   No animated shimmer — see the design note on [SpImageDefaults].
 *
 * Use this primitive whenever you'd otherwise reach for
 * `SubcomposeAsyncImage` or `AsyncImage` directly.
 *
 * @param model The image source. Typically a URL String. `null` skips the
 *   Coil call and renders [placeholder].
 * @param contentDescription Accessibility description forwarded to Coil.
 * @param modifier Outer modifier (size / shape / clipping).
 * @param contentScale How the image fits its bounds.
 * @param staggerMs Upper bound (inclusive) for the random pre-request
 *   delay. Set to 0 to fire the request immediately.
 * @param placeholder Composable shown while the stagger timer runs OR
 *   while Coil is loading. Defaults to [SpImageDefaults.Placeholder].
 * @param error Composable shown when Coil reports an error. Defaults to
 *   [SpImageDefaults.Placeholder] — visually indistinguishable so a
 *   loaded-vs-failed image doesn't flicker through different surfaces.
 * @param onSuccess Optional callback invoked when Coil reports success
 *   (e.g. for callers that want to read the painter's intrinsic size).
 */
@Composable
fun SpImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    staggerMs: Long = DEFAULT_STAGGER_MS,
    placeholder: @Composable () -> Unit = { SpImageDefaults.Placeholder() },
    error: @Composable () -> Unit = { SpImageDefaults.Placeholder() },
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    if (model == null) {
        Box(modifier = modifier) { placeholder() }
        return
    }

    // Gate the Coil request behind a random delay. The gate is keyed on
    // `model`: if the URL changes (e.g. a card is recycled into a
    // different game), the timer restarts so we don't show a stale
    // placeholder while waiting for a leftover timer on a different URL.
    var ready by remember(model, staggerMs) {
        mutableStateOf(staggerMs <= 0L)
    }
    LaunchedEffect(model, staggerMs) {
        if (staggerMs > 0L && !ready) {
            // Random.nextLong is half-open [0, bound). Add 1 so the
            // declared bound is inclusive — purely for tidiness, the
            // single-ms difference is irrelevant in practice.
            delay(Random.nextLong(0L, staggerMs + 1L))
            ready = true
        }
    }

    Box(modifier = modifier) {
        if (!ready) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                loading = { placeholder() },
                error = { error() },
                onSuccess = { state -> onSuccess?.invoke(state) },
            )
        }
    }
}

/**
 * Default slot composables for [SpImage].
 *
 * `Placeholder` is a static tinted Box. We deliberately do NOT animate
 * it — the previous shimmer pattern ran a `rememberInfiniteTransition`
 * per card, which on cold-paint of a many-card screen amounted to 100+
 * concurrent per-frame tickers fighting the composition thread. Image
 * arrival is its own "loading finished" signal; the shimmer didn't earn
 * its cost.
 *
 * If a future design calls for a load-in animation, add it here so a
 * single composable owns it for the whole app — never per-leaf.
 */
object SpImageDefaults {
    @Composable
    fun Placeholder() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.SurfaceBright.copy(alpha = 0.25f)),
        )
    }

    @Composable
    fun ErrorTinted() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
        )
    }
}
