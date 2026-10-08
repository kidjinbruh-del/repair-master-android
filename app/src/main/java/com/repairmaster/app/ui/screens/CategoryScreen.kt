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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.Category
import com.repairmaster.app.data.GuideItem
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim

@Composable
fun CategoryScreen(
    category: Category,
    onItem: (String) -> Unit,
    onProblem: (String) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(
                    category.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (category.summary.isNotBlank()) {
                    Text(
                        category.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                    )
                }
            }
        }

        items(category.items, key = { it.id }) { item ->
            ItemCard(item, onItem = { onItem(item.id) },
                onProblem = { pid -> onProblem(pid) })
        }
    }
}

@Composable
private fun ItemCard(item: GuideItem, onItem: () -> Unit, onProblem: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onItem)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim,
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(22.dp),
            )
        }

        if (item.problems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            item.problems.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel2, RoundedCornerShape(10.dp))
                        .clickable { onProblem(p.id) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ReportProblem,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        p.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        p.timeEstimate,
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}