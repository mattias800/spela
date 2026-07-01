package com.spela.player.presentation.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import kotlin.test.Test
import kotlin.test.assertNotNull

class SpCarouselPrefetchTest {

    // #1241: SpCarousel supplies a non-null PrefetchScheduler so Compose never
    // constructs the default Android one — AndroidPrefetchScheduler calls
    // Choreographer.getInstance(), which throws "The current thread must have a
    // looper!" when constructed on a non-looper thread under instrumentation and
    // kills the app process. LazyLayout resolves the scheduler as
    // `prefetchState.prefetchScheduler ?: default`, so if this ever regresses to
    // null the crashing default returns. This guards that invariant.
    @OptIn(ExperimentalFoundationApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun carouselSuppliesNonNullPrefetchScheduler() {
        assertNotNull(
            CarouselNoPrefetchStrategy.prefetchScheduler,
            "SpCarousel must provide a non-null PrefetchScheduler to avoid the Android default (#1241)",
        )
    }
}
