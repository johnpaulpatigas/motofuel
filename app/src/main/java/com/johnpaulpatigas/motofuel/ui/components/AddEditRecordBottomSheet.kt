package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorBorderColor = MaterialTheme.colorScheme.error,
        errorLabelColor = MaterialTheme.colorScheme.error,
        errorSupportingTextColor = MaterialTheme.colorScheme.error
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                label = { Text("Odometer Reading", fontWeight = FontWeight.Medium) },
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
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Text(
                        text = "km",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = litersText,
                onValueChange = { litersText = it },
                label = { Text("Fuel Added", fontWeight = FontWeight.Medium) },
                placeholder = { Text("e.g. 3.8") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Text(
                        text = "Liters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = textFieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price per Liter (Optional)", fontWeight = FontWeight.Medium) },
                placeholder = { Text("e.g. 62.50") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AttachMoney,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = textFieldColors,
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
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { onSave(odometerText, litersText, priceText.ifBlank { null }) },
                    enabled = odoWarning == null,
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
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
