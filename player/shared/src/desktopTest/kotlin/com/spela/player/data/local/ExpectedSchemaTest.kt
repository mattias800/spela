package com.spela.player.data.local

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [ExpectedSchema] matches the actual SQLDelight-generated schema.
 * If this test fails, it means SpelaDatabase.sq was changed without updating ExpectedSchema.kt.
 */
class ExpectedSchemaTest {

    @Test
    fun expectedSchemaMatchesActualDatabase() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            SpelaDatabase.Schema.create(it)
        }

        // Get actual tables
        val actualTables = driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                val tables = mutableSetOf<String>()
                while (cursor.next().value) {
                    cursor.getString(0)?.let { tables.add(it) }
                }
                app.cash.sqldelight.db.QueryResult.Value(tables)
            },
            parameters = 0,
        ).value

        // ExpectedSchema should list exactly the tables that exist
        val expectedTableNames = ExpectedSchema.tables.keys
        val missingFromExpected = actualTables - expectedTableNames
        val extraInExpected = expectedTableNames - actualTables
        assertTrue(
            missingFromExpected.isEmpty(),
            "Tables in DB but missing from ExpectedSchema: $missingFromExpected. Add them to ExpectedSchema.kt.",
        )
        assertTrue(
            extraInExpected.isEmpty(),
            "Tables in ExpectedSchema but not in DB: $extraInExpected. Remove them from ExpectedSchema.kt.",
        )

        // For each table, check columns match
        for ((table, expectedColumns) in ExpectedSchema.tables) {
            val actualColumns = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA table_info($table)",
                mapper = { cursor ->
                    val cols = mutableSetOf<String>()
                    while (cursor.next().value) {
                        cursor.getString(1)?.let { cols.add(it) }
                    }
                    app.cash.sqldelight.db.QueryResult.Value(cols)
                },
                parameters = 0,
            ).value

            val missingCols = actualColumns - expectedColumns
            val extraCols = expectedColumns - actualColumns
            assertEquals(
                emptySet(),
                missingCols,
                "Table $table: columns in DB but missing from ExpectedSchema: $missingCols. Add them to ExpectedSchema.kt.",
            )
            assertEquals(
                emptySet(),
                extraCols,
                "Table $table: columns in ExpectedSchema but not in DB: $extraCols. Remove them from ExpectedSchema.kt.",
            )
        }

        driver.close()
    }
}
