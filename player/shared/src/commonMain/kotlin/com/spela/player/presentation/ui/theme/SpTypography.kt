package com.spela.player.presentation.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Centralised typography tokens for the player UI.
 *
 * ## Minimum size floor: 12sp
 *
 * Every token defined here is ≥ 12sp. The app's main UI must not use
 * smaller text — readability on TVs, gaming handhelds, and for users
 * with imperfect eyesight degrades quickly below that. 16sp is the
 * default body / title size; 14sp is the comfortable mid tier; 12sp
 * is the smallest size the app will go to (used only for the
 * densest secondary metadata: chip labels, supporting captions).
 *
 * **Do not hardcode `fontSize = N.sp`** outside this file. If a token
 * doesn't fit your case, the answer is either to pick a different
 * tier or to move the information somewhere it has room — not to go
 * smaller than 12sp.
 *
 * ## Tier semantics
 *
 * - Display: hero / splash banners. Rare.
 * - Headline: section titles ("Top Rated", "About this developer").
 * - Title: card titles, list-item primary text.
 * - Body: paragraphs, descriptions, metadata values.
 * - Label: buttons, chips, tags, supporting metadata labels.
 *
 * Within a tier, *Small / Medium / Large are visual-weight knobs —
 * they're all readable in the main UI.
 */
object SpTypography {
    // Display - for hero sections, splash screens
    val DisplayLarge = TextStyle(
        fontSize = 48.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    )
    val DisplayMedium = TextStyle(
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
    )
    val DisplaySmall = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp,
    )

    // Headline - section titles
    val HeadlineLarge = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
    )
    val HeadlineMedium = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    )
    val HeadlineSmall = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    )

    // Title - card titles, list items
    val TitleLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    )
    val TitleMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    )
    val TitleSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )

    // Body - descriptions, paragraphs
    val BodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    )
    val BodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )
    val BodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    )

    // Label - buttons, chips, tags
    val LabelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )
    val LabelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
    // 12sp — the floor. Used to be 10sp, which is unreadable at
    // typical gaming-handheld / TV viewing distance and for users
    // with imperfect eyesight. See the kdoc on this object.
    val LabelSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    )
}
