package com.spela.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Flags composables that control their own outer horizontal spacing by applying
 * `.padding(horizontal = SpSpacing.ScreenHorizontal)` on their root layout element.
 *
 * The design-system rule: components never control their own outer spacing — the
 * caller passes outer padding via the `modifier` parameter. Components that bake
 * `ScreenHorizontal` into their root layout cannot be embedded in containers that
 * already provide horizontal padding (they double-pad) or in narrower containers.
 *
 * False-positive avoidance: the "root layout" is the outermost layout call inside
 * the @Composable function body, not nested inside any lambda. A call-site
 * composition like `OtherComposable(modifier = Modifier.padding(horizontal = ScreenHorizontal))`
 * passes padding to a child via modifier — that's the legitimate direction and
 * is not flagged.
 *
 * Edge-to-edge exceptions (hero + inset header patterns) are not detected as
 * root layouts because the hero element is itself a layout call — so the
 * overall pattern is "multiple top-level layouts", each of which has its own
 * modifier. Those files can whitelist via detekt.yml `excludes`.
 */
class ComponentOuterSpacingRule(config: Config) : Rule(
    config,
    "Composables must not control their own outer horizontal spacing. " +
        "Move `.padding(horizontal = SpSpacing.ScreenHorizontal)` to the call site.",
) {

    private val layoutFunctionNames = setOf(
        "Row",
        "Column",
        "Box",
        "BoxWithConstraints",
        "LazyColumn",
        "LazyRow",
        "LazyVerticalGrid",
        "LazyHorizontalGrid",
        "FlowRow",
        "FlowColumn",
        "SpCard",
        "SpInnerCard",
        "SpTitledSection",
        "SpScreen",
        "SpSectionList",
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName !in layoutFunctionNames) return

        // Only consider calls that are the root layout of a @Composable function.
        if (!isRootLayoutOfComposable(expression)) return

        // Find the `modifier` named argument.
        val modifierArg = expression.valueArguments.firstOrNull { arg ->
            arg.getArgumentName()?.asName?.asString() == "modifier"
        } ?: return

        val modifierExpr = modifierArg.getArgumentExpression() ?: return

        if (modifierChainHasScreenHorizontalPadding(modifierExpr)) {
            report(
                Finding(
                    Entity.from(modifierArg),
                    "Composable's root $calleeName applies `.padding(horizontal = SpSpacing.ScreenHorizontal)`. " +
                        "Components must not control their own outer spacing — move this to the call site " +
                        "via `Modifier.padding(horizontal = SpSpacing.ScreenHorizontal)` in the parent. " +
                        "See IMPROVEMENTS.md and PRs #364-#370 for the full rule history.",
                ),
            )
        }
    }

    /**
     * A call expression is the "root layout" of a composable if:
     *   1. Its nearest enclosing KtNamedFunction is annotated @Composable.
     *   2. There's no KtLambdaExpression or KtFunctionLiteral between the call and that function.
     *
     * This correctly excludes inline Column/Row/Box layouts nested inside `item { }`,
     * `content = { }`, or other lambda-bounded contexts.
     */
    private fun isRootLayoutOfComposable(call: KtCallExpression): Boolean {
        for (parent in call.parents) {
            if (parent is KtLambdaExpression || parent is KtFunctionLiteral) {
                return false
            }
            if (parent is KtNamedFunction) {
                return parent.annotationEntries.any { it.shortName?.asString() == "Composable" }
            }
        }
        return false
    }

    /**
     * Walks the `modifier` argument's chain looking for `.padding(horizontal = SpSpacing.ScreenHorizontal)`
     * (or the `start = ..., end = ...` equivalent). The chain is left-recursive: `a.b().c().d()`
     * parses as `((a.b()).c()).d()`, so we walk `receiverExpression` backwards.
     */
    private fun modifierChainHasScreenHorizontalPadding(expr: KtExpression): Boolean {
        var current: KtExpression? = expr
        while (current is KtDotQualifiedExpression) {
            val selector = current.selectorExpression
            if (selector is KtCallExpression && selector.calleeExpression?.text == "padding") {
                if (paddingCallUsesScreenHorizontal(selector)) {
                    return true
                }
            }
            current = current.receiverExpression
        }
        return false
    }

    private fun paddingCallUsesScreenHorizontal(padding: KtCallExpression): Boolean {
        val args = padding.valueArguments
        // Positional single arg: padding(SpSpacing.ScreenHorizontal) → resolves to
        // padding(all: Dp) and applies ScreenHorizontal to all four sides,
        // including left/right — so it controls horizontal outer spacing.
        if (args.size == 1 && args[0].getArgumentName() == null) {
            val valueText = args[0].getArgumentExpression()?.text?.trim()
            if (valueText == "SpSpacing.ScreenHorizontal") {
                return true
            }
        }
        for (arg in args) {
            val name = arg.getArgumentName()?.asName?.asString()
            val valueText = arg.getArgumentExpression()?.text?.trim() ?: continue
            if (name == "horizontal" && valueText == "SpSpacing.ScreenHorizontal") {
                return true
            }
            if (name == "all" && valueText == "SpSpacing.ScreenHorizontal") {
                return true
            }
            if ((name == "start" || name == "end") && valueText == "SpSpacing.ScreenHorizontal") {
                return true
            }
        }
        return false
    }
}
