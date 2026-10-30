package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AssumptionsInfoDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Estimation Methodology & Assumptions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SectionTitle("1. Odometer Baseline Behavior")
                SectionBody("The first recorded refueling establishes the odometer baseline. Because the initial amount of fuel already present in the tank is unknown, fuel added during this baseline fill cannot be linked to prior distance. Interval calculations begin strictly from the second record onward.")

                Spacer(modifier = Modifier.height(12.dp))

                SectionTitle("2. Partial Refuelings & Fuel Level Variance")
                SectionBody("Fuel economy cannot be determined with 100% precision from arbitrary partial refueling records alone. When a rider tops off partially, fuel added does not equal fuel consumed over that interval. Motorcycle tilt (side-stand vs center-stand) can also create small variations in fuel level at fill-up.")

                Spacer(modifier = Modifier.height(12.dp))

                SectionTitle("3. Recursive Least Squares (RLS) Filter")
                SectionBody("To handle measurement noise and partial fill-ups without fabricating ground truth, MotoFuel uses an on-device Recursive Least Squares (RLS) regression algorithm with exponential forgetting. The algorithm continuously estimates the slope theta in: Distance = theta * Fuel.")

                Spacer(modifier = Modifier.height(12.dp))

                SectionTitle("4. Honest Uncertainty & Statistical Limits")
                SectionBody("Rather than claiming artificial AI accuracy, MotoFuel reports standard error and 95% confidence bounds. Adding more records reduces random variation but does not guarantee higher precision if riding conditions or partial fill-up patterns fluctuate significantly.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        },
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SectionBody(text: String) {
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
