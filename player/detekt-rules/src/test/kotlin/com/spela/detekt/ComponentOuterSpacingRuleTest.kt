package com.spela.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentOuterSpacingRuleTest {

    private val rule = ComponentOuterSpacingRule(Config.empty)

    @Test
    fun `flags horizontal named arg on root layout`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun Foo() {
                Column(modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal)) { }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size, "Expected one finding")
    }

    @Test
    fun `flags positional all arg on root layout`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun Foo() {
                Column(modifier = Modifier.padding(SpSpacing.ScreenHorizontal)) { }
            }
        """.trimIndent()
        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected positional .padding(SpSpacing.ScreenHorizontal) to be flagged")
    }

    @Test
    fun `flags named all arg on root layout`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun Foo() {
                Column(modifier = Modifier.padding(all = SpSpacing.ScreenHorizontal)) { }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size, "Expected one finding")
    }

    @Test
    fun `does not flag call-site composition passing padding via modifier`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable fun Child(modifier: Modifier = Modifier) { }
            @Composable
            fun Parent() {
                Column {
                    Child(modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal))
                }
            }
        """.trimIndent()
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "Call-site padding-through-modifier is legitimate, got: $findings")
    }

    @Test
    fun `does not flag nested layouts inside lambdas`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Row
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun Parent() {
                Column {
                    Row(modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal)) { }
                }
            }
        """.trimIndent()
        val findings = rule.lint(code)
        assertTrue(findings.isEmpty(), "Nested layout inside lambda should not be flagged, got: $findings")
    }

    @Test
    fun `flags BoxWithConstraints root with remember preamble`() {
        val code = """
            import androidx.compose.foundation.layout.BoxWithConstraints
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.remember
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun KeyMappingScreen(layout: String) {
                val buttonStates = remember(layout) { layout }
                val mappingLabels = remember(buttonStates) { buttonStates }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(SpSpacing.ScreenHorizontal),
                ) { }
            }
        """.trimIndent()
        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected BoxWithConstraints root with remember preamble to be flagged, got: $findings")
    }

    @Test
    fun `flags BoxWithConstraints root with positional padding`() {
        val code = """
            import androidx.compose.foundation.layout.BoxWithConstraints
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20 }
            @Composable
            fun KeyMappingScreen() {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(SpSpacing.ScreenHorizontal),
                ) { }
            }
        """.trimIndent()
        val findings = rule.lint(code)
        assertEquals(1, findings.size, "Expected BoxWithConstraints root to be flagged, got: $findings")
    }

    @Test
    fun `does not flag padding with different value`() {
        val code = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            object SpSpacing { val ScreenHorizontal = 20; val Medium = 16 }
            @Composable
            fun Foo() {
                Column(modifier = Modifier.padding(horizontal = SpSpacing.Medium)) { }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size, "Expected no findings")
    }
}
