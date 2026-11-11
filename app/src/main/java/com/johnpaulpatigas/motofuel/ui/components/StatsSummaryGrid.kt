package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import kotlin.math.roundToInt

/**
 * Summary grid displaying supporting motorcycle metrics:
 * estimated full-tank range, total fuel spend, cost per kilometer, and total distance.
 */
@Composable
fun StatsSummaryGrid(
    estimation: EstimationResult,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isCapacityUnset = estimation.tankCapacityLiters <= 0.0
            StatCard(
                title = "Full Tank Range",
                value = estimation.estimatedRangeKm?.let { "~${com.johnpaulpatigas.motofuel.util.FormatUtils.formatKm(it)} km" } ?: "---",
                subtitle = if (!isCapacityUnset) {
                    "On ${com.johnpaulpatigas.motofuel.util.FormatUtils.formatOneDecimalClean(estimation.tankCapacityLiters)} L tank"
                } else {
                    "Set capacity in Settings →"
                },
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                onClick = if (isCapacityUnset) onNavigateToSettings else null,
                subtitleColor = if (isCapacityUnset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Fuel Spend",
                value = "${estimation.currencySymbol}${formatOneDecimal(estimation.totalSpendCost)}",
                subtitle = "Total spent",
                icon = Icons.Default.Payments,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Cost per Kilometer & Total Distance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Cost per Kilometer",
                value = estimation.costPerKm?.let { "${estimation.currencySymbol}${formatTwoDecimals(it)} / km" } ?: "---",
                subtitle = "Average cost",
                icon = Icons.Default.LocalGasStation,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = "Total Distance",
                value = "${com.johnpaulpatigas.motofuel.util.FormatUtils.formatKm(estimation.totalDistanceKm)} km",
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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val cardColors = if (onClick != null) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    Card(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.medium,
        colors = cardColors,
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
                color = subtitleColor,
                fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private fun formatOneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
private fun formatTwoDecimals(value: Double): String = ((value * 100).roundToInt() / 100.0).toString()
