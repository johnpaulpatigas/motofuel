package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecordBottomSheet(
    recordToEdit: RefuelingRecord?,
    lastRecord: RefuelingRecord? = null,
    validationError: String?,
    currencySymbol: String = "₱",
    onDismiss: () -> Unit,
    onSave: (odometerStr: String, litersStr: String, priceStr: String?) -> Unit
) {
    val isEdit = recordToEdit != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var odometerText by remember(recordToEdit) {
        mutableStateOf(recordToEdit?.odometerKm?.let { formatClean(it) } ?: "")
    }
    var litersText by remember(recordToEdit) {
        mutableStateOf(recordToEdit?.litersAdded?.let { formatClean(it) } ?: "")
    }
    var priceText by remember(recordToEdit) {
        mutableStateOf(recordToEdit?.pricePerLiter?.let { formatClean(it) } ?: "")
    }

    val currentOdo = odometerText.toDoubleOrNull()
    val prevOdo = lastRecord?.odometerKm

    val odoWarning = if (!isEdit && prevOdo != null && currentOdo != null && currentOdo <= prevOdo) {
        "Odometer must be greater than previous (${formatClean(prevOdo)} km)"
    } else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (isEdit) "Edit Refueling Log" else "Add Refueling Log",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isEdit) "Modify the odometer reading and liters filled." else "Enter your current dash odometer reading and total fuel added at the pump.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = odometerText,
                onValueChange = { odometerText = it },
                label = { Text("Odometer Reading") },
                placeholder = { Text("e.g. 12450.0") },
                supportingText = {
                    Text(
                        text = odoWarning ?: if (prevOdo != null) "Last record: ${formatClean(prevOdo)} km" else "First entry acts as baseline."
                    )
                },
                isError = odoWarning != null,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Text(
                        text = "km",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = litersText,
                onValueChange = { litersText = it },
                label = { Text("Fuel Added") },
                placeholder = { Text("e.g. 3.8") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Text(
                        text = "Liters",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price per Liter (Optional)") },
                placeholder = { Text("e.g. 62.50") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            validationError?.let { err ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onSave(odometerText, litersText, priceText.ifBlank { null }) },
                    enabled = odoWarning == null,
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Text(
                        text = if (isEdit) "Save Changes" else "Add Record",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatClean(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
