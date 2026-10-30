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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.R
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.ui.components.DashboardHeroCard
import com.johnpaulpatigas.motofuel.ui.components.EfficiencyChangeCard
import com.johnpaulpatigas.motofuel.ui.components.RefuelingHistoryList
import com.johnpaulpatigas.motofuel.ui.components.StatsSummaryGrid
import com.johnpaulpatigas.motofuel.ui.viewmodel.FuelUiState

/**
 * Overview tab content presenting top hero estimate, CUSUM efficiency-change warnings,
 * key metrics grid, and recent refueling activity preview.
 *
 * Implements pure state and lambda event hoisting for previewability and testability.
 */
@Composable
fun OverviewScreen(
    uiState: FuelUiState,
    onNavigateToLogs: () -> Unit,
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

        // MotoFuel Title Header with Icon
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
                        painter = painterResource(id = R.drawable.ic_motorcycle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "MotoFuel",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Hero Card
        DashboardHeroCard(
            estimation = uiState.estimation
        )

        // Efficiency Change Alert Card (displayed only when CUSUM signal is triggered)
        EfficiencyChangeCard(
            signal = uiState.estimation.efficiencyChangeSignal
        )

        // Key Metrics Grid
        StatsSummaryGrid(
            estimation = uiState.estimation
        )

        // Recent Activity Preview
        Column {
            RefuelingHistoryList(
                records = uiState.records,
                targetKmLiters = uiState.estimation.targetKmLiters,
                onEditClick = onEditRecord,
                onDeleteClick = onDeleteRecord,
                maxItems = 3
            )

            if (uiState.records.size > 3) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onNavigateToLogs,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("View Analytics & All ${uiState.records.size} Logs →", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
