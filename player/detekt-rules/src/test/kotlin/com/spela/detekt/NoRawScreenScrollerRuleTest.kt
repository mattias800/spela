package com.spela.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NoRawScreenScrollerRuleTest {

    private val rule = NoRawScreenScrollerRule(Config.empty)

    @Test
    fun `flags raw LazyColumn`() {
        val code = """
            @Composable
            fun Foo() {
                LazyColumn { }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size, "Expected one finding for raw LazyColumn")
    }

    @Test
    fun `flags raw LazyVerticalGrid`() {
        val code = """
            @Composable
            fun Foo() {
                LazyVerticalGrid(columns = GridCells.Fixed(2)) { }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size, "Expected one finding for raw LazyVerticalGrid")
    }

    @Test
    fun `does not flag the shared layout components`() {
        val code = """
            @Composable
            fun Foo() {
                SpScreenContentList { }
                SpLazyVerticalGrid(columns = GridCells.Fixed(2)) { }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size, "Shared components must not be flagged")
    }
}
