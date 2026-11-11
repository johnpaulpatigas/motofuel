package com.johnpaulpatigas.motofuel.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.ui.components.FuelTrendChart
import com.johnpaulpatigas.motofuel.ui.components.RefuelingHistoryList
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import com.johnpaulpatigas.motofuel.ui.viewmodel.FuelUiState
import com.johnpaulpatigas.motofuel.util.FormatUtils

/**
 * Analytics and complete logs screen presenting fuel economy trend,
 * interval extremes, and month-grouped refueling records.
 */
@Composable
fun AnalyticsScreen(
    uiState: FuelUiState,
    onEditRecord: (RefuelingRecord) -> Unit,
    onDeleteRecord: (RefuelingRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Analytics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        AnalyticsSummaryRow(
            records = uiState.records,
            currentKmLiters = uiState.estimation.predictedKmLiters
        )

        FuelTrendChart(
            trendPoints = uiState.estimation.trendPoints,
            targetKmLiters = uiState.estimation.targetKmLiters
        )

        RefuelingHistoryList(
            records = uiState.records,
            targetKmLiters = uiState.estimation.targetKmLiters,
            currencySymbol = uiState.estimation.currencySymbol,
            onEditClick = onEditRecord,
            onDeleteClick = onDeleteRecord
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun AnalyticsSummaryRow(
    records: List<RefuelingRecord>,
    currentKmLiters: Double?
) {
    val rlsValues = androidx.compose.runtime.remember(records) {
        records.mapNotNull { it.currentKmLiters }
    }
    if (rlsValues.isEmpty()) return

    val (best, worst) = androidx.compose.runtime.remember(rlsValues) {
        Pair(
            rlsValues.maxOrNull() ?: 0.0,
            rlsValues.minOrNull() ?: 0.0
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryMetricCard(
            label = "BEST RECORDED",
            value = "${FormatUtils.formatOneDecimal(best)} km/L",
            color = MaterialTheme.semanticColors.good,
            modifier = Modifier.weight(1f)
        )
        SummaryMetricCard(
            label = "CURRENT EFFICIENCY",
            value = currentKmLiters?.let { "${FormatUtils.formatOneDecimal(it)} km/L" } ?: "---",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SummaryMetricCard(
            label = "WORST RECORDED",
            value = "${FormatUtils.formatOneDecimal(worst)} km/L",
            color = MaterialTheme.semanticColors.critical,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
