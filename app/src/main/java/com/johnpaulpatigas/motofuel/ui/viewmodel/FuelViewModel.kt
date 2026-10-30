package com.johnpaulpatigas.motofuel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.johnpaulpatigas.motofuel.data.local.PreferencesManager
import com.johnpaulpatigas.motofuel.data.model.AppTheme
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.data.repository.RefuelingRepository
import com.johnpaulpatigas.motofuel.data.repository.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SettingsConfig(
    val tankCapacityLiters: Double,
    val targetKmLiters: Double,
    val currencySymbol: String
)

/**
 * ViewModel managing MotoFuel UI state and user interactions.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FuelViewModel @Inject constructor(
    private val repository: RefuelingRepository,
    private val preferencesManager: PreferencesManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FuelUiState(
            appTheme = preferencesManager?.appTheme ?: AppTheme.DARK
        )
    )
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    private val _settingsConfig = MutableStateFlow(
        SettingsConfig(
            tankCapacityLiters = preferencesManager?.tankCapacityLiters ?: 0.0,
            targetKmLiters = preferencesManager?.targetKmLiters ?: 0.0,
            currencySymbol = preferencesManager?.currencySymbol ?: "₱"
        )
    )

    init {
        viewModelScope.launch {
            combine(
                repository.getDerivedRecords(),
                _settingsConfig
            ) { records, settings ->
                records to settings
            }.flatMapLatest { (records, settings) ->
                repository.getEstimationResult(
                    tankCapacityLiters = settings.tankCapacityLiters,
                    targetKmLiters = settings.targetKmLiters,
                    currencySymbol = settings.currencySymbol
                ).map { estimation ->
                    records to estimation
                }
            }.collect { (records, estimation) ->
                _uiState.update { currentState ->
                    currentState.copy(
                        records = records,
                        estimation = estimation
                    )
                }
            }
        }
    }

    fun setAppTheme(theme: AppTheme) {
        preferencesManager?.appTheme = theme
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun updateMotorcycleSpecsAndTarget(capacityStr: String, targetStr: String) {
        val cap = capacityStr.trim().toDoubleOrNull() ?: 0.0
        val target = targetStr.trim().toDoubleOrNull() ?: 0.0

        preferencesManager?.tankCapacityLiters = cap
        preferencesManager?.targetKmLiters = target

        _settingsConfig.update {
            it.copy(
                tankCapacityLiters = cap,
                targetKmLiters = target
            )
        }
    }

    fun setTankCapacity(capacityStr: String) {
        val cap = capacityStr.trim().toDoubleOrNull() ?: 0.0
        preferencesManager?.tankCapacityLiters = cap
        _settingsConfig.update { it.copy(tankCapacityLiters = cap) }
    }

    fun setTargetKmLiters(targetStr: String) {
        val target = targetStr.trim().toDoubleOrNull() ?: 0.0
        preferencesManager?.targetKmLiters = target
        _settingsConfig.update { it.copy(targetKmLiters = target) }
    }

    fun setCurrencySymbol(symbol: String) {
        preferencesManager?.currencySymbol = symbol
        _settingsConfig.update { it.copy(currencySymbol = symbol) }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = true, validationError = null) }
    }

    fun closeAddDialog() {
        _uiState.update { it.copy(isAddDialogOpen = false, validationError = null) }
    }

    fun openEditDialog(record: RefuelingRecord) {
        _uiState.update { it.copy(recordToEdit = record, validationError = null) }
    }

    fun closeEditDialog() {
        _uiState.update { it.copy(recordToEdit = null, validationError = null) }
    }

    fun openDeleteDialog(record: RefuelingRecord) {
        _uiState.update { it.copy(recordToDelete = record) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(recordToDelete = null) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun addRecord(odometerStr: String, litersStr: String, priceStr: String? = null) {
        val odometer = odometerStr.trim().toDoubleOrNull()
        val liters = litersStr.trim().toDoubleOrNull()
        val price = priceStr?.trim()?.toDoubleOrNull()

        if (odometer == null || liters == null) {
            _uiState.update { it.copy(validationError = "Please enter valid numbers for both fields.") }
            return
        }

        viewModelScope.launch {
            when (val result = repository.validateAndAdd(odometer, liters, price)) {
                is ValidationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isAddDialogOpen = false,
                            validationError = null,
                            userMessage = "Refueling record saved."
                        )
                    }
                }
                is ValidationResult.Error -> {
                    _uiState.update { it.copy(validationError = result.message) }
                }
            }
        }
    }

    fun updateRecord(id: Long, odometerStr: String, litersStr: String, priceStr: String? = null) {
        val odometer = odometerStr.trim().toDoubleOrNull()
        val liters = litersStr.trim().toDoubleOrNull()
        val price = priceStr?.trim()?.toDoubleOrNull()

        if (odometer == null || liters == null) {
            _uiState.update { it.copy(validationError = "Please enter valid numbers for both fields.") }
            return
        }

        viewModelScope.launch {
            when (val result = repository.validateAndUpdate(id, odometer, liters, price)) {
                is ValidationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            recordToEdit = null,
                            validationError = null,
                            userMessage = "Refueling record updated."
                        )
                    }
                }
                is ValidationResult.Error -> {
                    _uiState.update { it.copy(validationError = result.message) }
                }
            }
        }
    }

    fun confirmDeleteRecord() {
        val record = _uiState.value.recordToDelete ?: return
        viewModelScope.launch {
            repository.deleteRecord(record.id)
            _uiState.update {
                it.copy(
                    recordToDelete = null,
                    userMessage = "Record deleted. Estimates recalculated."
                )
            }
        }
    }

    fun openClearAllDialog() {
        _uiState.update { it.copy(isClearAllDialogOpen = true) }
    }

    fun closeClearAllDialog() {
        _uiState.update { it.copy(isClearAllDialogOpen = false) }
    }

    fun confirmClearAllData() {
        viewModelScope.launch {
            repository.deleteAllRecords()
            _uiState.update {
                it.copy(
                    isClearAllDialogOpen = false,
                    userMessage = "All records cleared from local database."
                )
            }
        }
    }

    fun exportDataToUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val json = repository.exportDataJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                _uiState.update { it.copy(userMessage = "Data successfully exported!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    fun importDataFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText()
                } ?: ""

                val isJson = content.trim().startsWith("[") || content.trim().startsWith("{")
                val result = if (isJson) {
                    repository.importDataJson(content)
                } else {
                    repository.importDataCsv(content)
                }

                when (result) {
                    is ValidationResult.Success -> {
                        _uiState.update {
                            it.copy(userMessage = "Data successfully imported! All estimates recalculated.")
                        }
                    }
                    is ValidationResult.Error -> {
                        _uiState.update { it.copy(userMessage = result.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Import failed: ${e.localizedMessage}") }
            }
        }
    }

    class Factory(
        private val repository: RefuelingRepository,
        private val preferencesManager: PreferencesManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FuelViewModel::class.java)) {
                return FuelViewModel(repository, preferencesManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
