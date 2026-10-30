package com.johnpaulpatigas.motofuel.ui.viewmodel

import com.johnpaulpatigas.motofuel.data.model.AppTheme
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord

/**
 * UI State for MotoFuel main screen.
 */
data class FuelUiState(
    val records: List<RefuelingRecord> = emptyList(),
    val estimation: EstimationResult = EstimationResult(),
    val appTheme: AppTheme = AppTheme.DARK,
    val isAddDialogOpen: Boolean = false,
    val recordToEdit: RefuelingRecord? = null,
    val recordToDelete: RefuelingRecord? = null,
    val isClearAllDialogOpen: Boolean = false,
    val validationError: String? = null,
    val userMessage: String? = null
)
