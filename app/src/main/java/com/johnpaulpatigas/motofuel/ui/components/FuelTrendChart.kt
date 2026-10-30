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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnpaulpatigas.motofuel.data.model.TrendPoint
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import com.johnpaulpatigas.motofuel.util.FormatUtils
import kotlin.math.abs

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

    val axisValueLabelStyle = remember(typography, colorScheme) {
        typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
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
                        text = "Fuel Economy Trend (RLS Filter)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                val outlineColor = MaterialTheme.colorScheme.outlineVariant
                val gridLineColor = MaterialTheme.colorScheme.outlineVariant
                val activePointColor = MaterialTheme.colorScheme.tertiary
                val targetLineColor = MaterialTheme.semanticColors.good

                val minRls = trendPoints.minOf { it.rlsPredictedKmLiters ?: 0.0 }
                val maxRls = trendPoints.maxOf { it.rlsPredictedKmLiters ?: 0.0 }
                val minRaw = trendPoints.minOfOrNull { it.intervalKmLiters ?: Double.MAX_VALUE }?.takeIf { it != Double.MAX_VALUE } ?: minRls
                val maxRaw = trendPoints.maxOfOrNull { it.intervalKmLiters ?: Double.MIN_VALUE }?.takeIf { it != Double.MIN_VALUE } ?: maxRls

                val rawMinOdo = trendPoints.minOf { it.odometerKm }
                val rawMaxOdo = trendPoints.maxOf { it.odometerKm }

                val overallMin = minOf(minRls, minRaw, if (targetKmLiters > 0) targetKmLiters else minRls)
                val overallMax = maxOf(maxRls, maxRaw, if (targetKmLiters > 0) targetKmLiters else maxRls)

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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Odo: ${FormatUtils.formatKm(currentPoint.odometerKm)} km",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                currentPoint.intervalKmLiters?.let { raw ->
                                    Text(
                                        text = "Raw Interval: ${FormatUtils.formatOneDecimal(raw)} km/L",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Filtered RLS: ${FormatUtils.formatOneDecimal(currentPoint.rlsPredictedKmLiters ?: 0.0)} km/L",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                val gridDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
                val targetDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) }
                val activeDashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) }

                val cachedFillPath = remember { Path() }
                val cachedLinePath = remember { Path() }

                // Pre-measure all text layouts to avoid allocations inside the Canvas draw loop
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
                            text = "Target Goal: ${FormatUtils.formatOneDecimal(targetKmLiters)} km/L",
                            style = axisLabelStyle.copy(color = targetLineColor, fontWeight = FontWeight.Bold)
                        )
                    } else null
                }

                val pointValueLabels = remember(trendPoints, axisValueLabelStyle) {
                    trendPoints.map { pt ->
                        pt.rlsPredictedKmLiters?.let { rls ->
                            textMeasurer.measure(
                                text = FormatUtils.formatOneDecimal(rls),
                                style = axisValueLabelStyle
                            )
                        }
                    }
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

                    // Compute Screen Offsets
                    val offsets = trendPoints.map { pt ->
                        val xRatio = ((pt.odometerKm - xMin) / (xMax - xMin)).toFloat().coerceIn(0f, 1f)
                        val yRatio = (((pt.rlsPredictedKmLiters ?: 0.0) - yMin) / (yMax - yMin)).toFloat().coerceIn(0f, 1f)

                        val xPos = leftMargin + xRatio * chartWidth
                        val yPos = topMargin + chartHeight * (1f - yRatio)
                        Offset(xPos, yPos)
                    }

                    // 4. Gradient Area Fill under RLS Line
                    if (offsets.size >= 2) {
                        val visibleCount = (offsets.size * animatedProgress).toInt().coerceIn(2, offsets.size)
                        val visibleOffsets = offsets.take(visibleCount)

                        cachedFillPath.reset()
                        cachedFillPath.apply {
                            moveTo(visibleOffsets[0].x, topMargin + chartHeight)
                            visibleOffsets.forEach { lineTo(it.x, it.y) }
                            lineTo(visibleOffsets.last().x, topMargin + chartHeight)
                            close()
                        }

                        drawPath(
                            path = cachedFillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.25f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = topMargin,
                                endY = topMargin + chartHeight
                            )
                        )

                        // Main RLS Line
                        cachedLinePath.reset()
                        cachedLinePath.apply {
                            moveTo(visibleOffsets[0].x, visibleOffsets[0].y)
                            for (i in 1 until visibleOffsets.size) {
                                lineTo(visibleOffsets[i].x, visibleOffsets[i].y)
                            }
                        }

                        drawPath(
                            path = cachedLinePath,
                            color = primaryColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 6. Interactive Points & Labels
                    offsets.forEachIndexed { idx, pt ->
                        val isSelected = idx == selectedIndex

                        val valResult = pointValueLabels.getOrNull(idx)
                        if (valResult != null) {
                            val yLabelPos = if (pt.y - 18.dp.toPx() < topMargin) {
                                pt.y + 8.dp.toPx()
                            } else {
                                pt.y - 16.dp.toPx()
                            }
                            val xLabelPos = (pt.x - valResult.size.width / 2f).coerceIn(
                                leftMargin,
                                size.width - rightMargin - valResult.size.width
                            )
                            drawText(
                                textLayoutResult = valResult,
                                topLeft = Offset(xLabelPos, yLabelPos)
                            )
                        }

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
                                radius = 4.dp.toPx(),
                                center = pt
                            )
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
                        offsets.forEachIndexed { idx, pt ->
                            drawLine(
                                color = outlineColor,
                                start = Offset(pt.x, topMargin + chartHeight),
                                end = Offset(pt.x, topMargin + chartHeight + 4.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            val labelResult = odoTickLabels.getOrNull(idx)
                            if (labelResult != null) {
                                val labelX = when (idx) {
                                    0 -> pt.x
                                    trendPoints.lastIndex -> pt.x - labelResult.size.width
                                    else -> pt.x - labelResult.size.width / 2f
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
