package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.TrendPoint
import kotlin.math.roundToInt

@Composable
fun StatsSummaryGrid(
    estimation: EstimationResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Text(
            text = "METRICS OVERVIEW",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        // Row 1: Full Tank Range & Fuel Spend
        if (estimation.tankCapacityLiters > 0.0 || estimation.totalSpendCost > 0.0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (estimation.tankCapacityLiters > 0.0) {
                    StatCard(
                        title = "Full Tank Range",
                        value = estimation.estimatedRangeKm?.let { "~${formatOneDecimal(it)} km" } ?: "---",
                        subtitle = "On ${formatOneDecimal(estimation.tankCapacityLiters)} L tank",
                        icon = Icons.AutoMirrored.Filled.DirectionsBike,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (estimation.totalSpendCost > 0.0) {
                    StatCard(
                        title = "Fuel Spend",
                        value = "${estimation.currencySymbol}${formatOneDecimal(estimation.totalSpendCost)}",
                        subtitle = estimation.costPerKm?.let { "${estimation.currencySymbol}${formatTwoDecimals(it)} / km" } ?: "Total spent",
                        icon = Icons.Default.Payments,
                        modifier = Modifier.weight(1f)
                    )
                } else if (estimation.tankCapacityLiters <= 0.0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Row 2: Recent Fuel Economy & Overall Fuel Economy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SparklineStatCard(
                title = "Recent Fuel Economy",
                value = estimation.recentKmLiters?.let { "${formatOneDecimal(it)} km/L" } ?: "---",
                subtitle = "Last 3 refuels",
                trendPoints = estimation.trendPoints,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Overall Fuel Economy",
                value = estimation.cumulativeKmLiters?.let { "${formatOneDecimal(it)} km/L" } ?: "---",
                subtitle = "${estimation.intervalCount} intervals",
                icon = Icons.Default.Functions,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Efficiency Trend & Total Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Efficiency Trend",
                value = estimation.predictedKmLiters?.let { "${formatOneDecimal(it)} km/L" } ?: "---",
                subtitle = "RLS filter estimate",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Total Distance",
                value = "${formatOneDecimal(estimation.totalDistanceKm)} km",
                subtitle = "Since baseline",
                icon = Icons.Default.Route,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(modifier = Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SparklineStatCard(
    title: String,
    value: String,
    subtitle: String,
    trendPoints: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(modifier = Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AvTimer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Mini Canvas Sparkline
                val validValues = remember(trendPoints) {
                    trendPoints.mapNotNull { it.intervalKmLiters ?: it.rlsPredictedKmLiters }.takeLast(6)
                }
                if (validValues.size >= 2) {
                    val sparklinePath = remember { Path() }
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 22.dp)
                            .padding(bottom = 2.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                            val minVal = validValues.minOrNull() ?: 0.0
                            val maxVal = validValues.maxOrNull() ?: 1.0
                            val range = (maxVal - minVal).coerceAtLeast(0.1)

                            val stepX = size.width / (validValues.size - 1)
                            sparklinePath.reset()

                            validValues.forEachIndexed { idx, valItem ->
                                val x = idx * stepX
                                val normalizedY = ((valItem - minVal) / range).toFloat()
                                val y = size.height - (normalizedY * (size.height - 4.dp.toPx())) - 2.dp.toPx()

                                if (idx == 0) {
                                    sparklinePath.moveTo(x, y)
                                } else {
                                    sparklinePath.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = sparklinePath,
                                color = primaryColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatOneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
private fun formatTwoDecimals(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
