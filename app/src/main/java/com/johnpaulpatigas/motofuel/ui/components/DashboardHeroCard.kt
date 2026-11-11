package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.ReliabilityLevel
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hero card displaying overall fuel economy (km/L) and target progress with statistical confidence.
 */
@Composable
fun DashboardHeroCard(
    estimation: EstimationResult,
    modifier: Modifier = Modifier
) {
    var showExplanationDialog by remember { mutableStateOf(false) }

    val overall = estimation.cumulativeKmLiters
    val target = estimation.targetKmLiters
    val uncertainty = estimation.uncertaintyMargin
    val diff = estimation.efficiencyVsTarget

    val animatedOverall by animateFloatAsState(
        targetValue = (overall ?: 0.0).toFloat(),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "HeroOverallAnimation"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(
                            modifier = Modifier.padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Fuel Economy",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "FUEL ECONOMY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                ConfidenceBadge(
                    level = estimation.reliability,
                    onClick = { showExplanationDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (overall != null && estimation.reliability != ReliabilityLevel.INSUFFICIENT_DATA) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = formatOneDecimal(animatedOverall.toDouble()),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            text = "km/L",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (uncertainty != null && uncertainty > 0.0) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            onClick = { showExplanationDialog = true },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "±${formatOneDecimal(uncertainty)} error",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Explain error margin",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Progress Bar vs Goal Target
                if (target > 0.0) {
                    Spacer(modifier = Modifier.height(16.dp))

                    val progressRatio = (overall / target).coerceIn(0.0, 1.2).toFloat() / 1.2f
                    val isGood = diff != null && diff >= 0.0
                    val isNear = diff != null && diff >= -2.0 && diff < 0.0

                    val barColor = when {
                        isGood -> MaterialTheme.semanticColors.good
                        isNear -> MaterialTheme.semanticColors.warning
                        else -> MaterialTheme.colorScheme.primary
                    }

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Target: ${com.johnpaulpatigas.motofuel.util.FormatUtils.formatOneDecimalClean(target)} km/L",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (diff != null) {
                            val diffStr = if (diff >= 0.0) "+${formatOneDecimal(diff)}" else formatOneDecimal(diff)
                            val (pillContainer, pillText, pillIcon) = when {
                                isGood -> Triple(
                                    MaterialTheme.semanticColors.goodContainer,
                                    MaterialTheme.semanticColors.onGoodContainer,
                                    Icons.AutoMirrored.Filled.TrendingUp
                                )
                                isNear -> Triple(
                                    MaterialTheme.semanticColors.warningContainer,
                                    MaterialTheme.semanticColors.onWarningContainer,
                                    Icons.AutoMirrored.Filled.TrendingUp
                                )
                                else -> Triple(
                                    MaterialTheme.semanticColors.criticalContainer,
                                    MaterialTheme.semanticColors.onCriticalContainer,
                                    Icons.AutoMirrored.Filled.TrendingDown
                                )
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = pillContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = pillIcon,
                                        contentDescription = null,
                                        tint = pillText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$diffStr km/L vs goal",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = pillText
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "--- km/L",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add at least 2 refueling entries to start estimating fuel economy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showExplanationDialog) {
        ErrorMarginExplanationDialog(
            estimation = estimation,
            onDismiss = { showExplanationDialog = false }
        )
    }
}

@Composable
private fun ConfidenceBadge(
    level: ReliabilityLevel,
    onClick: () -> Unit
) {
    val (label, containerColor, textColor) = when (level) {
        ReliabilityLevel.INSUFFICIENT_DATA -> Triple(
            "Data Needed",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReliabilityLevel.LOW -> Triple(
            "Low Confidence",
            MaterialTheme.semanticColors.criticalContainer,
            MaterialTheme.semanticColors.onCriticalContainer
        )
        ReliabilityLevel.MEDIUM -> Triple(
            "Medium Confidence",
            MaterialTheme.semanticColors.warningContainer,
            MaterialTheme.semanticColors.onWarningContainer
        )
        ReliabilityLevel.HIGH -> Triple(
            "High Confidence",
            MaterialTheme.semanticColors.goodContainer,
            MaterialTheme.semanticColors.onGoodContainer
        )
    }

    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = textColor
        ),
        border = null,
        shape = MaterialTheme.shapes.small
    )
}

@Composable
private fun ErrorMarginExplanationDialog(
    estimation: EstimationResult,
    onDismiss: () -> Unit
) {
    val overall = estimation.cumulativeKmLiters ?: 0.0
    val uncertainty = estimation.uncertaintyMargin ?: 0.0
    val lower = max(0.0, overall - uncertainty)
    val upper = overall + uncertainty
    val count = estimation.intervalCount
    val dist = estimation.totalDistanceKm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Error Margin (±${formatOneDecimal(uncertainty)} km/L)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Expected Range Surface
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "95% CONFIDENCE RANGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatOneDecimal(lower)} - ${formatOneDecimal(upper)} km/L",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Estimated true fuel economy based on $count interval logs across ${formatOneDecimal(dist)} km.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }

                Text(
                    text = "Why is it ±${formatOneDecimal(uncertainty)} km/L?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "This margin accounts for real-world fuel log variations, including:\n" +
                            "• Partial vs full fill-ups (fuel added doesn't always equal fuel consumed)\n" +
                            "• Pump nozzle cut-off differences and motorcycle tilt\n" +
                            "• Variations between city and highway riding",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Why this specific value?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (count < 5) {
                        "With $count recorded interval(s), statistical sample size is still growing. Smaller sample sizes result in a wider error margin."
                    } else {
                        "Calculated from variance across your $count refueling intervals. As you log more consistent fill-ups, statistical variance decreases."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "How to narrow the margin:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Continue logging refueling entries after riding\n" +
                            "• Fill the tank to full when possible\n" +
                            "• Ensure accurate odometer entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun formatOneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()
