package com.spela.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags raw `LazyColumn` / `LazyVerticalGrid` in screen code.
 *
 * The design-system rule: screens compose from shared layout components, and
 * those components carry the common behavior. The shared scrollers
 * (`SpScreenContentList` for a list, `SpLazyVerticalGrid` for a grid) wire
 * right-stick scrolling and focus-centering internally, so a screen using them
 * gets that behavior automatically and can never silently miss it. A screen that
 * drops down to a raw `LazyColumn` / `LazyVerticalGrid` bypasses all of it.
 *
 * Scope this rule to screen files via detekt.yml `includes` — the shared
 * wrapper components legitimately wrap these primitives and are outside that
 * scope.
 */
class NoRawScreenScrollerRule(config: Config) : Rule(
    config,
    "Screens must use the shared SpScreenContentList / SpLazyVerticalGrid layout " +
        "components, not raw LazyColumn / LazyVerticalGrid.",
) {

    private val forbidden = setOf("LazyColumn", "LazyVerticalGrid")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName !in forbidden) return

        report(
            Finding(
                Entity.from(expression),
                "Raw `$calleeName` in a screen. Use the shared layout component " +
                    "(`SpScreenContentList` for a list, `SpLazyVerticalGrid` for a grid) so " +
                    "right-stick scrolling and focus-centering are wired automatically. " +
                    "See player/LAYOUT.md.",
            ),
        )
    }
}
