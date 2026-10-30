package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.engine.EfficiencyChangeType
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors

/**
 * Card alerting the rider when sequential change detection (CUSUM) identifies
 * a persistent drop or improvement in fuel efficiency relative to long-term baseline.
 *
 * Employs clear, non-diagnostic terminology as mandated by project safety standards.
 */
@Composable
fun EfficiencyChangeCard(
    signal: EfficiencyChangeType,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = signal != EfficiencyChangeType.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val semantic = MaterialTheme.semanticColors

        val (containerColor, contentColor, icon, title, description) = when (signal) {
            EfficiencyChangeType.EFFICIENCY_DROP -> Tuple5(
                semantic.warningContainer,
                semantic.onWarningContainer,
                Icons.AutoMirrored.Filled.TrendingDown,
                "Possible Efficiency Decrease",
                "Recent fuel economy is lower than your long-term average baseline. Consider checking tire pressure, fuel quality, or recent riding conditions."
            )
            EfficiencyChangeType.EFFICIENCY_IMPROVEMENT -> Tuple5(
                semantic.goodContainer,
                semantic.onGoodContainer,
                Icons.AutoMirrored.Filled.TrendingUp,
                "Possible Efficiency Improvement",
                "Recent fuel economy shows a notable improvement above your long-term average baseline."
            )
            EfficiencyChangeType.NONE -> return@AnimatedVisibility
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = contentColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

private data class Tuple5<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
