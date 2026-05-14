package com.spela.player.presentation.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Centralised typography tokens for the player UI.
 *
 * ## Minimum size floor: 14sp
 *
 * Every token defined here is ≥ 14sp. The app's main UI must not use
 * smaller text — readability on TVs, gaming handhelds, and for users
 * with imperfect eyesight degrades quickly below that. 16sp is the
 * default body / title size; 14sp is reserved for the genuinely
 * smaller tier (labels, supporting metadata, chips).
 *
 * If you find yourself wanting smaller text, you almost certainly
 * want to:
 *
 *   1. Move information to a different place in the layout, or
 *   2. Use [FinePrint] explicitly — the one allowed exception, for
 *      true disclaimers / legal-style text that the user shouldn't
 *      need to read often.
 *
 * **Do not hardcode `fontSize = N.sp`** outside this file (with the
 * narrow exception of in-game overlays where dense layouts are
 * unavoidable — e.g. the secondary keyboard tab labels).
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
    // 14sp — at the readability floor. Used to be 12sp; bumped per the
    // app-wide minimum (see kdoc above).
    val TitleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
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
    // 14sp — at the readability floor. Used to be 12sp; bumped per the
    // app-wide minimum (see kdoc above).
    val BodySmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )

    // Label - buttons, chips, tags
    val LabelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    )
    // 14sp — at the readability floor. Used to be 12sp; bumped per the
    // app-wide minimum (see kdoc above).
    val LabelMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    )
    // 14sp — at the readability floor. Used to be 10sp; bumped per the
    // app-wide minimum (see kdoc above).
    val LabelSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    )

    /**
     * Disclaimer / legal-style fine print. 12sp — the ONE allowed
     * exception to the 14sp floor.
     *
     * Reserved for text that:
     *
     *   - The user should be aware of but doesn't need to read in
     *     daily use (terms-of-service blurbs, attribution footers,
     *     debug-build markers, ROM hash signatures shown for
     *     verification, etc.)
     *   - Is information-dense enough that compressing it down
     *     actively helps comprehension (e.g. a tabular "this is
     *     metadata about the metadata" footer).
     *
     * If you're tempted to use this for ordinary supporting metadata
     * (release year, developer name, chip text), use [LabelSmall]
     * (14sp) instead.
     */
    val FinePrint = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    )
}
