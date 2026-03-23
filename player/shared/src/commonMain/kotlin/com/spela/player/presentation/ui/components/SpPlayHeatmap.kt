package com.spela.player.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.HeatmapEntry
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlin.math.min
import kotlin.time.Clock

private fun colorForPlayTime(seconds: Long) = when {
    seconds <= 0 -> SpColor.SurfaceBright.copy(alpha = 0.3f)
    seconds < 30 * 60 -> SpColor.Primary.copy(alpha = 0.2f)     // < 30 minutes
    seconds < 60 * 60 -> SpColor.Primary.copy(alpha = 0.4f)     // 30-60 minutes
    seconds < 120 * 60 -> SpColor.Primary.copy(alpha = 0.7f)    // 60-120 minutes
    else -> SpColor.Primary                                      // 120+ minutes
}

private val MonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

private val DayLabels = listOf("", "Mon", "", "Wed", "", "Fri", "")

private data class HeatmapCell(val date: String, val playTime: Long)
private data class MonthLabel(val column: Int, val name: String)

private data class HeatmapGridData(
    val grid: Array<Array<HeatmapCell?>>,
    val monthLabels: List<MonthLabel>,
)

@Composable
fun SpPlayHeatmap(
    entries: List<HeatmapEntry>,
    modifier: Modifier = Modifier,
) {
    val tz = TimeZone.currentSystemDefault()
    val today = remember { Clock.System.now().toLocalDateTime(tz).date }
    val rows = 7
    val cellSize = 12.dp
    val cellGap = SpSpacing.XXSmall
    val cellStep = cellSize + cellGap
    val monthLabelHeight = 14.dp
    val monthLabelGap = SpSpacing.XSmall
    val cornerRadius = SpSpacing.XXSmall

    // Build lookup: date string -> playTime (seconds)
    val playTimeMap = remember(entries) {
        entries.associate { it.date to it.playTime }
    }

    val textMeasurer = rememberTextMeasurer()
    val monthLabelStyle = SpTypography.LabelSmall.copy(color = SpColor.OnBackgroundTertiary)

    val density = LocalDensity.current

    // Day label column width estimate (enough for "Wed")
    val dayLabelWidth = 28.dp
    val dayLabelSpacing = SpSpacing.XSmall

    BoxWithConstraints(
        modifier = modifier.testTag("user_profile_heatmap"),
    ) {
        val availableWidthDp = maxWidth - dayLabelWidth - dayLabelSpacing
        val cellStepPx = with(density) { cellStep.toPx() }
        val availableWidthPx = with(density) { availableWidthDp.toPx() }
        val weeks = min(52, (availableWidthPx / cellStepPx).toInt())

        // Calculate grid data
        val gridData = remember(today, playTimeMap, weeks) {
            // Today's day-of-week: Monday=1 .. Sunday=7 (ISO)
            // DayOfWeek ordinal: MONDAY=0 .. SUNDAY=6, so ISO day number = ordinal + 1
            val todayDow = today.dayOfWeek.ordinal + 1
            // The last column ends on today's row. Total days covered:
            val totalDays = (weeks - 1) * 7 + todayDow
            // Start date is totalDays-1 before today
            val startDate = today.plus(-(totalDays - 1), DateTimeUnit.DAY)

            // Build grid: weeks x 7 (rows: 0=Mon, 6=Sun)
            val grid = Array(weeks) { col ->
                Array(rows) { row ->
                    val dayOffset = col * 7 + row
                    if (dayOffset < totalDays) {
                        val date = startDate.plus(dayOffset, DateTimeUnit.DAY)
                        val dateStr = date.toString()
                        val playTime = playTimeMap[dateStr] ?: 0L
                        HeatmapCell(dateStr, playTime)
                    } else {
                        null
                    }
                }
            }

            // Month labels: find columns where a new month starts
            val labels = mutableListOf<MonthLabel>()
            var lastMonthOrdinal = -1
            for (col in 0 until weeks) {
                val dayOffset = col * 7
                if (dayOffset < (weeks - 1) * 7 + todayDow) {
                    val date = startDate.plus(dayOffset, DateTimeUnit.DAY)
                    val monthOrdinal = date.month.ordinal
                    if (monthOrdinal != lastMonthOrdinal) {
                        labels.add(MonthLabel(col, MonthNames[monthOrdinal]))
                        lastMonthOrdinal = monthOrdinal
                    }
                }
            }

            HeatmapGridData(grid, labels)
        }

        val gridWidth = cellStep * weeks - cellGap
        val gridHeight = monthLabelHeight + monthLabelGap + cellStep * rows - cellGap

        Row {
            // Day labels on the left
            Column(
                modifier = Modifier
                    .width(dayLabelWidth)
                    .height(gridHeight + monthLabelHeight + monthLabelGap),
            ) {
                Spacer(Modifier.height(monthLabelHeight + monthLabelGap))
                DayLabels.forEach { label ->
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                            modifier = Modifier.height(cellStep),
                        )
                    } else {
                        Spacer(Modifier.height(cellStep))
                    }
                }
            }

            Spacer(Modifier.width(dayLabelSpacing))

            // Heatmap grid with month labels drawn on Canvas
            Canvas(
                modifier = Modifier
                    .width(gridWidth)
                    .height(gridHeight),
            ) {
                val cellSizePx = cellSize.toPx()
                val cellStepCanvasPx = cellStep.toPx()
                val cornerRadiusPx = cornerRadius.toPx()
                val gridTopPx = (monthLabelHeight + monthLabelGap).toPx()

                // Draw month labels
                for (monthLabel in gridData.monthLabels) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = monthLabel.name,
                        topLeft = Offset(monthLabel.column * cellStepCanvasPx, 0f),
                        style = monthLabelStyle,
                    )
                }

                // Draw cells
                for (col in gridData.grid.indices) {
                    val column = gridData.grid[col]
                    for (row in 0 until rows) {
                        val cell = column[row] ?: continue
                        val color = colorForPlayTime(cell.playTime)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(
                                x = col * cellStepCanvasPx,
                                y = gridTopPx + row * cellStepCanvasPx,
                            ),
                            size = Size(cellSizePx, cellSizePx),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        )
                    }
                }
            }
        }
    }
}
