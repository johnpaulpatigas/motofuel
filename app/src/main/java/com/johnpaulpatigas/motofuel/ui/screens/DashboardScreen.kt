package com.johnpaulpatigas.motofuel.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.ui.components.AddEditRecordBottomSheet
import com.johnpaulpatigas.motofuel.ui.components.ClearAllConfirmDialog
import com.johnpaulpatigas.motofuel.ui.components.DeleteConfirmDialog
import com.johnpaulpatigas.motofuel.ui.viewmodel.FuelViewModel

/**
 * Root navigation coordinator screen for MotoFuel.
 * Manages the Scaffold, floating navigation bar, FAB, and modal dialog routing.
 */
@Composable
fun DashboardScreen(
    viewModel: FuelViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { 3 }, initialPage = selectedTab)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissUserMessage()
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (selectedTab != pagerState.settledPage) {
            selectedTab = pagerState.settledPage
        }
    }

    // Graceful Back Button Handler
    val isAnyDialogOpen = uiState.isAddDialogOpen || uiState.recordToEdit != null || uiState.recordToDelete != null || uiState.isClearAllDialogOpen
    BackHandler(enabled = isAnyDialogOpen || selectedTab != 0) {
        when {
            uiState.isAddDialogOpen -> viewModel.closeAddDialog()
            uiState.recordToEdit != null -> viewModel.closeEditDialog()
            uiState.recordToDelete != null -> viewModel.closeDeleteDialog()
            uiState.isClearAllDialogOpen -> viewModel.closeClearAllDialog()
            selectedTab != 0 -> {
                selectedTab = 0
                coroutineScope.launch { pagerState.scrollToPage(0) }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Pager for swipe gesture navigation between tabs
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> OverviewScreen(
                        uiState = uiState
                    )
                    1 -> AnalyticsScreen(
                        uiState = uiState,
                        onEditRecord = { record -> viewModel.openEditDialog(record) },
                        onDeleteRecord = { record -> viewModel.openDeleteDialog(record) }
                    )
                    2 -> SettingsTabContent(
                        viewModel = viewModel
                    )
                }
            }

            // Floating Toolbar Overlay & Separate FAB Beside It (Bottom Center)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Floating Toolbar
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .animateContentSize(tween(250))
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                Triple(0, "Overview", Icons.Default.Speed),
                                Triple(1, "Analytics", Icons.Default.Analytics),
                                Triple(2, "Settings", Icons.Default.Settings)
                            )

                            tabs.forEach { (index, title, icon) ->
                                val isSelected = selectedTab == index
                                val activeContainerColor = MaterialTheme.colorScheme.primary
                                val activeContentColor = MaterialTheme.colorScheme.onPrimary
                                val inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant

                                Surface(
                                    onClick = { 
                                        selectedTab = index 
                                        coroutineScope.launch { pagerState.scrollToPage(index) }
                                    },
                                    shape = CircleShape,
                                    color = if (isSelected) activeContainerColor else Color.Transparent,
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .animateContentSize(tween(200))
                                            .padding(
                                                horizontal = if (isSelected) 14.dp else 10.dp,
                                                vertical = 10.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = title,
                                            tint = if (isSelected) activeContentColor else inactiveContentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = activeContentColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Floating Action Button
                    AnimatedVisibility(
                        visible = selectedTab != 2,
                        enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                        exit = fadeOut(tween(100)) + scaleOut(tween(100))
                    ) {
                        FloatingActionButton(
                            onClick = { viewModel.openAddDialog() },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Log Refuel",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Record Bottom Sheet
    if (uiState.isAddDialogOpen) {
        AddEditRecordBottomSheet(
            recordToEdit = null,
            lastRecord = uiState.records.lastOrNull(),
            validationError = uiState.validationError,
            currencySymbol = uiState.estimation.currencySymbol,
            onDismiss = { viewModel.closeAddDialog() },
            onSave = { odo, liters, price -> viewModel.addRecord(odo, liters, price) }
        )
    }

    // Edit Record Bottom Sheet
    uiState.recordToEdit?.let { record ->
        AddEditRecordBottomSheet(
            recordToEdit = record,
            lastRecord = uiState.records.dropLast(1).lastOrNull(),
            validationError = uiState.validationError,
            currencySymbol = uiState.estimation.currencySymbol,
            onDismiss = { viewModel.closeEditDialog() },
            onSave = { odo, liters, price -> viewModel.updateRecord(record.id, odo, liters, price) }
        )
    }

    // Delete Confirm Dialog
    uiState.recordToDelete?.let { record ->
        DeleteConfirmDialog(
            recordToDelete = record,
            onDismiss = { viewModel.closeDeleteDialog() },
            onConfirm = { viewModel.confirmDeleteRecord() }
        )
    }

    // Clear All Confirm Dialog
    if (uiState.isClearAllDialogOpen) {
        ClearAllConfirmDialog(
            onDismiss = { viewModel.closeClearAllDialog() },
            onConfirm = { viewModel.confirmClearAllData() }
        )
    }
}
