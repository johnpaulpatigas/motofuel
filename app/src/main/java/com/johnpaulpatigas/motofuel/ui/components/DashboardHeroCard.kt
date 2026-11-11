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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johnpaulpatigas.motofuel.data.model.EstimationResult
import com.johnpaulpatigas.motofuel.data.model.ReliabilityLevel
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import kotlin.math.roundToInt

/**
 * Hero card displaying overall fuel economy (km/L) and target progress.
 */
@Composable
fun DashboardHeroCard(
    estimation: EstimationResult,
    modifier: Modifier = Modifier
) {
    val overall = estimation.cumulativeKmLiters
    val target = estimation.targetKmLiters
    val diff = estimation.efficiencyVsTarget
    val intervalCount = estimation.intervalCount
    val totalDist = estimation.totalDistanceKm

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

                if (overall != null && intervalCount > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = "$intervalCount refuels logged",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (overall != null && estimation.reliability != ReliabilityLevel.INSUFFICIENT_DATA) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = com.johnpaulpatigas.motofuel.util.FormatUtils.formatOneDecimal(animatedOverall.toDouble()),
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
                }

                if (totalDist > 0.0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Average across ${com.johnpaulpatigas.motofuel.util.FormatUtils.formatKm(totalDist)} km total distance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                            val diffStr = if (diff >= 0.0) "+${com.johnpaulpatigas.motofuel.util.FormatUtils.formatOneDecimal(diff)}" else com.johnpaulpatigas.motofuel.util.FormatUtils.formatOneDecimal(diff)
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
                        text = "Add at least 2 refueling entries to start calculating fuel economy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
