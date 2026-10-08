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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.Category
import com.repairmaster.app.data.Problem
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim

private data class SearchHit(
    val problem: Problem?,
    val category: Category?,
    val title: String,
    val subtitle: String,
    val kind: String,
)

@Composable
fun SearchScreen(
    categories: List<Category>,
    onProblem: (String) -> Unit,
    onCategory: (String) -> Unit,
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val q = query.text.trim().lowercase()

    val hits: List<SearchHit> = remember(q, categories) {
        if (q.length < 2) emptyList() else buildList {
            categories.forEach { cat ->
                cat.items.forEach { item ->
                    val hayItem = (item.title + " " + item.summary).lowercase()
                    if (q in hayItem) {
                        add(SearchHit(null, cat, item.title,
                            "${cat.title} · раздел", "item"))
                    }
                    item.models.forEach { m ->
                        val hayModel = buildString {
                            append(m.name).append(' ')
                            append(m.brand).append(' ')
                            append(m.years).append(' ')
                            append(m.note)
                        }.lowercase()
                        if (q in hayModel) {
                            add(SearchHit(null, cat, m.name,
                                "${if (m.brand.isNotBlank()) m.brand + " · " else ""}модель",
                                "model"))
                        }
                        m.problems.forEach { p ->
                            val hay = buildString {
                                append(p.title).append(' ')
                                append(p.symptoms.joinToString(" ")).append(' ')
                                append(p.diagnostics.joinToString(" ") { it.title })
                            }.lowercase()
                            if (q in hay) {
                                add(SearchHit(p, cat, p.title,
                                    "${m.name} · ${p.symptoms.firstOrNull() ?: ""}",
                                    "problem"))
                            }
                        }
                    }
                    item.problems.forEach { p ->
                        val hay = buildString {
                            append(p.title).append(' ')
                            append(p.device).append(' ')
                            append(p.symptoms.joinToString(" ")).append(' ')
                            append(p.causes.joinToString(" ") { it.text }).append(' ')
                            append(p.diagnostics.joinToString(" ") { it.title }).append(' ')
                            append(p.tools.joinToString(" "))
                        }.lowercase()
                        if (q in hay) {
                            add(SearchHit(p, cat, p.title,
                                "${p.symptoms.firstOrNull() ?: p.device}", "problem"))
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            placeholder = { Text("симптом, компонент, инструмент…",
                color = TextDim) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Amber)
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(onClick = { query = TextFieldValue("") }) {
                        Icon(Icons.Filled.Close, contentDescription = null,
                            tint = TextDim)
                    }
                }
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Panel2,
                unfocusedContainerColor = Panel2,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )

        if (q.length < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Введите минимум 2 символа.\nНапример: «не включается», «ESR», «пайка»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                )
            }
            return@Column
        }

        if (hits.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = TextDim)
            }
            return@Column
        }

        Text(
            "Найдено: ${hits.size}",
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(hits) { hit ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .clickable {
                            if (hit.problem != null) onProblem(hit.problem.id)
                            else hit.category?.let { onCategory(it.id) }
                        }
                        .padding(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (hit.problem != null) Icons.Filled.ReportProblem
                        else Icons.Filled.Article,
                        contentDescription = null,
                        tint = if (hit.problem != null) Amber else Cyan,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            hit.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            hit.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}