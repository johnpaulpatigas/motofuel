package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnpaulpatigas.motofuel.data.model.TrendPoint
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import com.johnpaulpatigas.motofuel.util.FormatUtils
import kotlin.math.abs

enum class TrendChartMode(val label: String) {
    RLS_ADAPTIVE("RLS Trend"),
    ROLLING_WINDOW("Rolling W=5"),
    CUMULATIVE("Cumulative"),
    COMPARE_ALL("Compare All")
}

@Composable
fun FuelTrendChart(
    trendPoints: List<TrendPoint>,
    targetKmLiters: Double = 0.0,
    uncertaintyMargin: Double? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()

    var selectedChartMode by remember { mutableStateOf(TrendChartMode.RLS_ADAPTIVE) }

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "ChartDrawIn"
    )

    val axisLabelStyle = remember(typography, colorScheme) {
        typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariant
        )
    }

    val axisTitleStyle = remember(typography, colorScheme) {
        typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.primary
        )
    }

    var selectedIndex by remember(trendPoints) {
        mutableStateOf<Int?>(if (trendPoints.isNotEmpty()) trendPoints.lastIndex else null)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = "Fuel Trend",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fuel Economy Trends",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Mode Selector Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrendChartMode.entries.forEach { mode ->
                    val isSelected = selectedChartMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedChartMode = mode },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (trendPoints.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add at least 2 refueling logs to view line chart trend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                val gridLineColor = MaterialTheme.colorScheme.outlineVariant
                val activePointColor = MaterialTheme.colorScheme.error
                val targetLineColor = MaterialTheme.semanticColors.good

                // Collect values based on mode
                val allValues = trendPoints.flatMap { pt ->
                    listOfNotNull(
                        pt.rlsPredictedKmLiters,
                        pt.rollingWindowKmLiters,
                        pt.cumulativeKmLiters,
                        pt.intervalKmLiters
                    )
                }

                val rawMinOdo = trendPoints.minOf { it.odometerKm }
                val rawMaxOdo = trendPoints.maxOf { it.odometerKm }

                val minVal = allValues.minOrNull() ?: 10.0
                val maxVal = allValues.maxOrNull() ?: 50.0

                val overallMin = minOf(minVal, if (targetKmLiters > 0) targetKmLiters else minVal)
                val overallMax = maxOf(maxVal, if (targetKmLiters > 0) targetKmLiters else maxVal)

                val yMin = (overallMin * 0.85).coerceAtLeast(0.0)
                val yMax = (overallMax * 1.15).coerceAtLeast(yMin + 5.0)
                val xMin = rawMinOdo
                val xMax = if (rawMaxOdo == rawMinOdo) rawMaxOdo + 10.0 else rawMaxOdo

                // Selected Point Tooltip / Summary
                val currentPoint = selectedIndex?.let { trendPoints.getOrNull(it) }
                if (currentPoint != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Odometer: ${FormatUtils.formatKm(currentPoint.odometerKm)} km",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                currentPoint.intervalKmLiters?.let { raw ->
                                    Text(
                                        text = "Interval: ${FormatUtils.formatOneDecimal(raw)} km/L",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                currentPoint.cumulativeKmLiters?.let { cum ->
                                    Text(
                                        text = "Overall: ${FormatUtils.formatOneDecimal(cum)} km/L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                currentPoint.rollingWindowKmLiters?.let { roll ->
                                    Text(
                                        text = "Rolling (W=5): ${FormatUtils.formatOneDecimal(roll)} km/L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tertiaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                currentPoint.rlsPredictedKmLiters?.let { rls ->
                                    Text(
                                        text = "RLS: ${FormatUtils.formatOneDecimal(rls)} km/L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                val gridDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
                val targetDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) }
                val activeDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

                val cachedFillPath = remember { Path() }
                val cachedLinePath = remember { Path() }

                // Pre-measure all text layouts
                val yAxisHeader = remember(axisTitleStyle) {
                    textMeasurer.measure(
                        text = "Fuel Economy (km/L)",
                        style = axisTitleStyle
                    )
                }

                val xAxisTitleResult = remember(axisTitleStyle) {
                    textMeasurer.measure(
                        text = "Odometer Distance (km)",
                        style = axisTitleStyle
                    )
                }

                val yGridSteps = 3
                val yGridLabels = remember(yMin, yMax, axisLabelStyle) {
                    (0..yGridSteps).map { i ->
                        val fraction = i.toFloat() / yGridSteps
                        val yValue = yMin + (yMax - yMin) * fraction
                        textMeasurer.measure(
                            text = FormatUtils.formatOneDecimal(yValue),
                            style = axisLabelStyle
                        )
                    }
                }

                val targetGoalText = remember(targetKmLiters, yMin, yMax, targetLineColor, axisLabelStyle) {
                    if (targetKmLiters > 0.0 && targetKmLiters in yMin..yMax) {
                        textMeasurer.measure(
                            text = "Target: ${FormatUtils.formatOneDecimal(targetKmLiters)} km/L",
                            style = axisLabelStyle.copy(color = targetLineColor, fontWeight = FontWeight.Bold)
                        )
                    } else null
                }

                val odoTickLabels = remember(trendPoints, axisLabelStyle) {
                    trendPoints.map { pt ->
                        textMeasurer.measure(
                            text = "${FormatUtils.formatKm(pt.odometerKm)} km",
                            style = axisLabelStyle
                        )
                    }
                }

                // Interactive Canvas Chart
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .pointerInput(trendPoints) {
                            detectTapGestures { tapOffset ->
                                val newIdx = findNearestPointIndex(
                                    tapX = tapOffset.x,
                                    canvasWidth = size.width.toFloat(),
                                    leftMargin = 48.dp.toPx(),
                                    rightMargin = 16.dp.toPx(),
                                    trendPoints = trendPoints
                                )
                                if (newIdx != selectedIndex && newIdx != null) {
                                    selectedIndex = newIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                        .pointerInput(trendPoints) {
                            detectDragGestures { change, _ ->
                                val newIdx = findNearestPointIndex(
                                    tapX = change.position.x,
                                    canvasWidth = size.width.toFloat(),
                                    leftMargin = 48.dp.toPx(),
                                    rightMargin = 16.dp.toPx(),
                                    trendPoints = trendPoints
                                )
                                if (newIdx != selectedIndex && newIdx != null) {
                                    selectedIndex = newIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                ) {
                    val leftMargin = 48.dp.toPx()
                    val rightMargin = 16.dp.toPx()
                    val topMargin = 28.dp.toPx()
                    val bottomMargin = 40.dp.toPx()

                    val chartWidth = size.width - leftMargin - rightMargin
                    val chartHeight = size.height - topMargin - bottomMargin

                    // 1. Draw Y-Axis Title
                    drawText(
                        textLayoutResult = yAxisHeader,
                        topLeft = Offset(4.dp.toPx(), 2.dp.toPx())
                    )

                    // 2. Horizontal Grid Lines
                    for (i in 0..yGridSteps) {
                        val fraction = i.toFloat() / yGridSteps
                        val yPos = topMargin + chartHeight * (1f - fraction)

                        drawLine(
                            color = gridLineColor,
                            start = Offset(leftMargin, yPos),
                            end = Offset(size.width - rightMargin, yPos),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = gridDashPathEffect
                        )

                        val labelResult = yGridLabels.getOrNull(i)
                        if (labelResult != null) {
                            drawText(
                                textLayoutResult = labelResult,
                                topLeft = Offset(leftMargin - labelResult.size.width - 6.dp.toPx(), yPos - labelResult.size.height / 2f)
                            )
                        }
                    }

                    // 3. Target Goal Line
                    if (targetKmLiters > 0.0 && targetKmLiters in yMin..yMax && targetGoalText != null) {
                        val targetYRatio = ((targetKmLiters - yMin) / (yMax - yMin)).toFloat()
                        val targetY = topMargin + chartHeight * (1f - targetYRatio)

                        drawLine(
                            color = targetLineColor,
                            start = Offset(leftMargin, targetY),
                            end = Offset(size.width - rightMargin, targetY),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = targetDashPathEffect
                        )

                        drawText(
                            textLayoutResult = targetGoalText,
                            topLeft = Offset(size.width - rightMargin - targetGoalText.size.width, targetY - 14.dp.toPx())
                        )
                    }

                    // Helper to compute screen points for any series
                    fun computeSeriesOffsets(seriesExtractor: (TrendPoint) -> Double?): List<Offset?> {
                        return trendPoints.map { pt ->
                            val valItem = seriesExtractor(pt) ?: return@map null
                            val xRatio = ((pt.odometerKm - xMin) / (xMax - xMin)).toFloat().coerceIn(0f, 1f)
                            val yRatio = ((valItem - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)
                            Offset(leftMargin + xRatio * chartWidth, topMargin + chartHeight * (1f - yRatio))
                        }
                    }

                    // Draw a series line on Canvas
                    fun drawSeriesLine(offsets: List<Offset?>, color: Color, strokeWidthDp: Float = 2.5f) {
                        val validOffsets = offsets.filterNotNull()
                        if (validOffsets.size >= 2) {
                            val visibleCount = (validOffsets.size * animatedProgress).toInt().coerceIn(2, validOffsets.size)
                            val visible = validOffsets.take(visibleCount)

                            cachedLinePath.reset()
                            cachedLinePath.moveTo(visible[0].x, visible[0].y)
                            for (i in 1 until visible.size) {
                                cachedLinePath.lineTo(visible[i].x, visible[i].y)
                            }

                            drawPath(
                                path = cachedLinePath,
                                color = color,
                                style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Draw gradient fill under primary series
                    fun drawSeriesGradient(offsets: List<Offset?>, color: Color) {
                        val validOffsets = offsets.filterNotNull()
                        if (validOffsets.size >= 2) {
                            val visibleCount = (validOffsets.size * animatedProgress).toInt().coerceIn(2, validOffsets.size)
                            val visible = validOffsets.take(visibleCount)

                            cachedFillPath.reset()
                            cachedFillPath.moveTo(visible[0].x, topMargin + chartHeight)
                            visible.forEach { cachedFillPath.lineTo(it.x, it.y) }
                            cachedFillPath.lineTo(visible.last().x, topMargin + chartHeight)
                            cachedFillPath.close()

                            drawPath(
                                path = cachedFillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.02f)),
                                    startY = topMargin,
                                    endY = topMargin + chartHeight
                                )
                            )
                        }
                    }

                    val rlsOffsets = computeSeriesOffsets { it.rlsPredictedKmLiters }
                    val rollingOffsets = computeSeriesOffsets { it.rollingWindowKmLiters }
                    val cumulativeOffsets = computeSeriesOffsets { it.cumulativeKmLiters }

                    when (selectedChartMode) {
                        TrendChartMode.RLS_ADAPTIVE -> {
                            drawSeriesGradient(rlsOffsets, primaryColor)
                            drawSeriesLine(rlsOffsets, primaryColor, 3f)
                        }
                        TrendChartMode.ROLLING_WINDOW -> {
                            drawSeriesGradient(rollingOffsets, tertiaryColor)
                            drawSeriesLine(rollingOffsets, tertiaryColor, 3f)
                        }
                        TrendChartMode.CUMULATIVE -> {
                            drawSeriesGradient(cumulativeOffsets, secondaryColor)
                            drawSeriesLine(cumulativeOffsets, secondaryColor, 3f)
                        }
                        TrendChartMode.COMPARE_ALL -> {
                            drawSeriesLine(cumulativeOffsets, secondaryColor, 2f)
                            drawSeriesLine(rollingOffsets, tertiaryColor, 2f)
                            drawSeriesLine(rlsOffsets, primaryColor, 3f)
                        }
                    }

                    // Selected Point Guideline & Highlights
                    val activeOffsets = when (selectedChartMode) {
                        TrendChartMode.RLS_ADAPTIVE -> rlsOffsets
                        TrendChartMode.ROLLING_WINDOW -> rollingOffsets
                        TrendChartMode.CUMULATIVE -> cumulativeOffsets
                        TrendChartMode.COMPARE_ALL -> rlsOffsets
                    }

                    activeOffsets.forEachIndexed { idx, pt ->
                        if (pt != null) {
                            val isSelected = idx == selectedIndex
                            if (isSelected) {
                                drawLine(
                                    color = activePointColor,
                                    start = Offset(pt.x, topMargin),
                                    end = Offset(pt.x, topMargin + chartHeight),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = activeDashPathEffect
                                )

                                drawCircle(
                                    color = activePointColor.copy(alpha = 0.3f),
                                    radius = 10.dp.toPx(),
                                    center = pt
                                )

                                drawCircle(
                                    color = activePointColor,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                            } else {
                                drawCircle(
                                    color = primaryColor,
                                    radius = 3.5.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }

                    // Baseline & Ticks
                    drawLine(
                        color = outlineColor,
                        start = Offset(leftMargin, topMargin + chartHeight),
                        end = Offset(size.width - rightMargin, topMargin + chartHeight),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    if (trendPoints.isNotEmpty()) {
                        trendPoints.forEachIndexed { idx, pt ->
                            val xRatio = ((pt.odometerKm - xMin) / (xMax - xMin)).toFloat().coerceIn(0f, 1f)
                            val xPos = leftMargin + xRatio * chartWidth

                            drawLine(
                                color = outlineColor,
                                start = Offset(xPos, topMargin + chartHeight),
                                end = Offset(xPos, topMargin + chartHeight + 4.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            val labelResult = odoTickLabels.getOrNull(idx)
                            if (labelResult != null) {
                                val labelX = when (idx) {
                                    0 -> xPos
                                    trendPoints.lastIndex -> xPos - labelResult.size.width
                                    else -> xPos - labelResult.size.width / 2f
                                }.coerceIn(leftMargin, size.width - rightMargin - labelResult.size.width)

                                drawText(
                                    textLayoutResult = labelResult,
                                    topLeft = Offset(labelX, topMargin + chartHeight + 6.dp.toPx())
                                )
                            }
                        }

                        drawText(
                            textLayoutResult = xAxisTitleResult,
                            topLeft = Offset(
                                leftMargin + (chartWidth - xAxisTitleResult.size.width) / 2f,
                                topMargin + chartHeight + 22.dp.toPx()
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun findNearestPointIndex(
    tapX: Float,
    canvasWidth: Float,
    leftMargin: Float,
    rightMargin: Float,
    trendPoints: List<TrendPoint>
): Int? {
    if (trendPoints.isEmpty()) return null
    val chartWidth = canvasWidth - leftMargin - rightMargin
    if (chartWidth <= 0) return 0

    val minOdo = trendPoints.minOf { it.odometerKm }
    val maxOdo = trendPoints.maxOf { it.odometerKm }

    val rawRatio = ((tapX - leftMargin) / chartWidth).coerceIn(0f, 1f)
    val targetOdo = minOdo + (maxOdo - minOdo) * rawRatio

    return trendPoints.indices.minByOrNull { abs(trendPoints[it].odometerKm - targetOdo) }
}
