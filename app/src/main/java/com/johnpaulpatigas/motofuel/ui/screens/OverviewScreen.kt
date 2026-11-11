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
 * Overview dashboard presenting overall fuel economy hero card,
 * efficiency change alerts, key motorcycle metrics overview, and latest refueling log.
 */
@Composable
fun OverviewScreen(
    uiState: FuelUiState,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLogs: () -> Unit = {},
    onEditRecord: (RefuelingRecord) -> Unit = {},
    onDeleteRecord: (RefuelingRecord) -> Unit = {}
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

        DashboardHeroCard(
            estimation = uiState.estimation
        )

        EfficiencyChangeCard(
            signal = uiState.estimation.efficiencyChangeSignal
        )

        StatsSummaryGrid(
            estimation = uiState.estimation,
            onNavigateToSettings = onNavigateToSettings
        )

        // Latest Refueling Preview (Single entry to prevent redundancy with Analytics tab)
        if (uiState.records.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RefuelingHistoryList(
                    records = uiState.records,
                    targetKmLiters = uiState.estimation.targetKmLiters,
                    currencySymbol = uiState.estimation.currencySymbol,
                    onEditClick = onEditRecord,
                    onDeleteClick = onDeleteRecord,
                    maxItems = 1
                )

                if (uiState.records.size > 1) {
                    FilledTonalButton(
                        onClick = onNavigateToLogs,
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text(
                            text = "View All ${uiState.records.size} Logs & Analytics →",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
