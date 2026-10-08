package com.repairmaster.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.DeviceModel
import com.repairmaster.app.data.GuideItem
import com.repairmaster.app.ui.components.AssetImageBox
import com.repairmaster.app.ui.components.ContentBlockView
import com.repairmaster.app.ui.components.DataTable
import com.repairmaster.app.ui.components.WarningBox
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim

@Composable
fun ItemScreen(
    item: GuideItem,
    onProblem: (String) -> Unit,
) {
    val modelProblems = item.models.sumOf { it.problems.size }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDim,
                    )
                }
            }
        }

        items(item.images.size) { i ->
            Card { AssetImageBox(item.images[i]) }
        }

        items(item.blocks.size) { i ->
            Card { ContentBlockView(item.blocks[i]) }
        }

        items(item.warnings.size) { i ->
            WarningBox(item.warnings[i])
        }

        if (item.models.isNotEmpty()) {
            item {
                Text(
                    "Модели и серии: конструктивные особенности",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(item.models.size) { i ->
                ModelCard(item.models[i], onProblem)
            }
        }

        if (item.problems.isNotEmpty() || modelProblems > 0) {
            item {
                Text(
                    if (modelProblems > 0)
                        "Инструкции по проблемам (включая модельные): ${item.problems.size + modelProblems}"
                    else "Инструкции по проблемам: ${item.problems.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        items(item.problems.size) { i ->
            val p = item.problems[i]
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(12.dp))
                    .clickable { onProblem(p.id) }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Cyan, RoundedCornerShape(4.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${p.symptoms.size} симптомов · ${p.diagnostics.size} шагов диагностики",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) { content() }
}

/** Карточка модели: бренд/годы, конструктивная особенность, таблица, поломки. */
@Composable
private fun ModelCard(
    model: DeviceModel,
    onProblem: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            Text(
                model.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOf(model.brand, model.years).filter { it.isNotBlank() }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber,
                )
            }
        }
        if (model.note.isNotBlank()) {
            Text(
                model.note,
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }
        if (model.specs.isNotEmpty()) {
            DataTable(listOf("", ""), model.specs)
        }
        model.problems.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel2, RoundedCornerShape(10.dp))
                    .clickable { onProblem(p.id) }
                    .padding(11.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(Cyan, RoundedCornerShape(4.dp)),
                )
                Text(
                    p.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}