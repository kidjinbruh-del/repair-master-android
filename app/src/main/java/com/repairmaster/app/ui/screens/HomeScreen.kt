package com.repairmaster.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.Category
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Line
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim

fun categoryIcon(name: String): ImageVector = when (name) {
    "soldering" -> Icons.Filled.Whatshot
    "multimeter" -> Icons.Filled.Settings
    "components" -> Icons.Filled.ElectricalServices
    "wires" -> Icons.Filled.Cable
    "tools" -> Icons.Filled.Build
    "diagnostics" -> Icons.Filled.Checklist
    "schematics" -> Icons.Filled.DeviceHub
    "solder_tools" -> Icons.Filled.ContentCut
    else -> Icons.Filled.Science
}

@Composable
fun HomeScreen(
    categories: List<Category>,
    problemCount: Int,
    onCategory: (String) -> Unit,
    onProblem: (String) -> Unit,
    onSearch: (String) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "Мастер по ремонту",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Справочник электроника: пайка, измерения, компоненты, провода, диагностика",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel2, RoundedCornerShape(12.dp))
                        .clickable { onSearch("") }
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Поиск по симптомам и компонентам…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextDim,
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatBox("Разделов", categories.size.toString())
                StatBox("Инструкций", problemCount.toString())
                StatBox("Офлайн", "да")
            }
        }

        items(categories.filter { it.group == "technique" }, key = { it.id }) { cat ->
            CategoryCard(cat, onClick = { onCategory(cat.id) })
        }

        item {
            Text(
                "Справочник",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        items(categories.filter { it.group != "technique" }, key = { it.id }) { cat ->
            CategoryCard(cat, onClick = { onCategory(cat.id) })
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = Amber,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextDim)
    }
}

@Composable
private fun CategoryCard(cat: Category, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(Amber.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(cat.icon),
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    cat.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${cat.items.size} тем · ${cat.items.sumOf { it.problems.size }} проблем",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
            }
        }
        if (cat.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                cat.summary,
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }
    }
}