package com.repairmaster.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.Action
import com.repairmaster.app.data.Problem
import com.repairmaster.app.ui.components.AssetImageBox
import com.repairmaster.app.ui.components.SeverityBadge
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Green
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.Red
import com.repairmaster.app.ui.theme.TextDim

@Composable
fun ProblemScreen(problem: Problem) {
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
                    problem.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeverityBadge(problem.severity)
                    MetaChip(Icons.Filled.Timer, problem.timeEstimate, Cyan)
                    MetaChip(Icons.Filled.Build, problem.device, TextDim)
                }
            }
        }

        items(problem.images.size) { i ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) { AssetImageBox(problem.images[i]) }
        }

        if (problem.warnings.isNotEmpty()) {
            item { SectionLabel("Сначала безопасность", Red) }
            items(problem.warnings.size) { i ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Red.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Red,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            problem.warnings[i],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        if (problem.symptoms.isNotEmpty()) {
            item { SectionLabel("Симптомы", Amber) }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        problem.symptoms.forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        if (problem.causes.isNotEmpty()) {
            item { SectionLabel("Вероятные причины", Amber) }
            items(problem.causes.size) { i ->
                val c = problem.causes[i]
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            c.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (c.check.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Проверка: ${c.check}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextDim,
                            )
                        }
                    }
                }
            }
        }

        if (problem.diagnostics.isNotEmpty()) {
            item { SectionLabel("Диагностика — по шагам", Cyan) }
            items(problem.diagnostics.size) { i ->
                StepCard(
                    index = i + 1,
                    action = problem.diagnostics[i],
                    color = Cyan,
                    hint = "если не помогло — переходите к следующему шагу",
                )
            }
        }

        if (problem.fix.isNotEmpty()) {
            item { SectionLabel("Ремонт", Green) }
            items(problem.fix.size) { i ->
                StepCard(index = i + 1, action = problem.fix[i], color = Green)
            }
        }

        if (problem.tools.isNotEmpty()) {
            item { SectionLabel("Нужны инструменты", Amber) }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    problem.tools.take(4).forEach {
                        Box(
                            Modifier
                                .background(Panel2, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color,
            modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun StepCard(index: Int, action: Action, color: Color, hint: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", style = MaterialTheme.typography.labelSmall, color = color)
        }
        Column(Modifier.weight(1f)) {
            Text(
                action.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (action.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    action.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
            }
            if (hint != null && index == 1) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextDim,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = TextDim)
                }
            }
        }
    }
}