package com.johnpaulpatigas.motofuel.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnpaulpatigas.motofuel.data.model.RefuelingRecord
import com.johnpaulpatigas.motofuel.ui.theme.semanticColors
import com.johnpaulpatigas.motofuel.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RefuelingHistoryList(
    records: List<RefuelingRecord>,
    onEditClick: (RefuelingRecord) -> Unit,
    onDeleteClick: (RefuelingRecord) -> Unit,
    modifier: Modifier = Modifier,
    maxItems: Int? = null,
    targetKmLiters: Double = 0.0,
    currencySymbol: String = "₱"
) {
    var expandedMonths by remember { mutableStateOf(setOf<String>()) }
    var showAllLogs by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (maxItems != null) "Recent Activity" else "Refueling Logs (${records.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (maxItems == null && records.size > 3) {
                IconButton(
                    onClick = { showAllLogs = !showAllLogs },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (showAllLogs) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showAllLogs) "Show last 3 refuels" else "Show all ${records.size} refuels",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (records.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Refueling Logs Yet",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to add your baseline odometer reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val limitTo3 = maxItems != null || (!showAllLogs && records.size > 3)

            if (limitTo3) {
                val effectiveLimit = maxItems ?: 3
                val displayRecords = remember(records, effectiveLimit) { records.reversed().take(effectiveLimit) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            displayRecords.forEachIndexed { index, record ->
                                androidx.compose.runtime.key(record.id) {
                                    CompactHistoryRow(
                                        record = record,
                                        targetKmLiters = targetKmLiters,
                                        onEditClick = onEditClick,
                                        onDeleteClick = onDeleteClick
                                    )
                                    if (index < displayRecords.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (maxItems == null && records.size > 3) {
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { showAllLogs = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Text("Show All ${records.size} Logs", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                // Full Analytics mode: Grouped by Month Headers into container cards
                val groupedRecords = remember(records) {
                    records.reversed().groupBy { record ->
                        monthFormatThreadLocal.get()?.format(Date(record.timestamp)) ?: ""
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    groupedRecords.forEach { (monthHeader, monthRecords) ->
                        val isExpanded = expandedMonths.contains(monthHeader)
                        val displayMonthRecords = if (isExpanded || monthRecords.size <= 3) monthRecords else monthRecords.take(3)
                        val totalMonthCost = monthRecords.sumOf { it.totalCost ?: 0.0 }
                        val totalMonthDistance = monthRecords.sumOf { it.intervalKm ?: 0.0 }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Month Section Header with Distance, Cost, and Expand/Collapse Chevron
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = monthHeader.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (totalMonthDistance > 0.0) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Route,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${FormatUtils.formatKm(totalMonthDistance)} km",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    if (totalMonthCost > 0.0) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Payments,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "$currencySymbol${FormatUtils.formatOneDecimal(totalMonthCost)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    if (monthRecords.size > 3) {
                                        IconButton(
                                            onClick = {
                                                expandedMonths = if (isExpanded) {
                                                    expandedMonths - monthHeader
                                                } else {
                                                    expandedMonths + monthHeader
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Show all ${monthRecords.size} logs",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column {
                                    displayMonthRecords.forEachIndexed { index, record ->
                                        androidx.compose.runtime.key(record.id) {
                                            CompactHistoryRow(
                                                record = record,
                                                targetKmLiters = targetKmLiters,
                                                onEditClick = onEditClick,
                                                onDeleteClick = onDeleteClick
                                            )
                                            if (index < displayMonthRecords.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 14.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHistoryRow(
    record: RefuelingRecord,
    targetKmLiters: Double,
    onEditClick: (RefuelingRecord) -> Unit,
    onDeleteClick: (RefuelingRecord) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading Icon Box
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (record.isBaseline) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (record.isBaseline) Icons.Default.Flag else Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = if (record.isBaseline) MaterialTheme.colorScheme.onSecondaryContainer
                           else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Odometer reading & detail info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${FormatUtils.formatKm(record.odometerKm)} km",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                record.intervalKm?.let { interval ->
                    if (interval > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(+${FormatUtils.formatKm(interval)} km)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val costStr = record.totalCost?.let { if (it > 0.0) " • ₱${FormatUtils.formatOneDecimal(it)}" else "" } ?: ""
            Text(
                text = "${FormatUtils.formatOneDecimal(record.litersAdded)} L$costStr • ${formatDate(record.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Efficiency Badge & Menu
        if (record.isBaseline) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Baseline",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (record.intervalKmLiters != null) {
            val valKmL = record.intervalKmLiters
            val semanticColors = MaterialTheme.semanticColors

            val (containerColor, textColor, icon) = when {
                targetKmLiters > 0.0 && valKmL >= targetKmLiters -> Triple(
                    semanticColors.goodContainer,
                    semanticColors.onGoodContainer,
                    Icons.AutoMirrored.Filled.TrendingUp
                )
                targetKmLiters > 0.0 && valKmL >= targetKmLiters - 2.0 -> Triple(
                    semanticColors.warningContainer,
                    semanticColors.onWarningContainer,
                    Icons.AutoMirrored.Filled.TrendingUp
                )
                targetKmLiters > 0.0 -> Triple(
                    semanticColors.criticalContainer,
                    semanticColors.onCriticalContainer,
                    Icons.AutoMirrored.Filled.TrendingDown
                )
                valKmL >= 30.0 -> Triple(
                    semanticColors.goodContainer,
                    semanticColors.onGoodContainer,
                    Icons.AutoMirrored.Filled.TrendingUp
                )
                else -> Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    Icons.AutoMirrored.Filled.TrendingUp
                )
            }

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = containerColor
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${FormatUtils.formatOneDecimal(valKmL)} km/L",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }

        // Overflow Menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit Entry") },
                    onClick = {
                        menuExpanded = false
                        onEditClick(record)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete Entry", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onDeleteClick(record)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

private val dateFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
}

private val monthFormatThreadLocal = ThreadLocal.withInitial {
    SimpleDateFormat("MMMM yyyy", Locale.getDefault())
}

private fun formatDate(timestamp: Long): String {
    return dateFormatThreadLocal.get()?.format(Date(timestamp)) ?: ""
}
