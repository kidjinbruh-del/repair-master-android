package com.repairmaster.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import com.repairmaster.app.data.parts.PartsDb
import com.repairmaster.app.data.parts.PartsRepository
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim

/**
 * Каталог встроенной базы деталей: список с фильтром по названию и типу.
 * Работает полностью офлайн.
 */
@Composable
fun PartsCatalogScreen(
    repo: PartsRepository,
    onPick: (PartsDb.Entry) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    val all = remember { repo.allEntries() }
    val types = remember(all) { all.map { it.type }.distinct().sorted() }

    val rows = remember(filter, typeFilter, all) {
        val f = filter.trim().lowercase()
        all.asSequence()
            .filter { typeFilter.isBlank() || it.type == typeFilter }
            .filter {
                f.isBlank() ||
                    it.marking.lowercase().contains(f) ||
                    it.name.lowercase().contains(f) ||
                    it.equivalents.any { e -> e.lowercase().contains(f) }
            }
            .sortedBy { it.marking }
            .toList()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Фильтр: маркировка, название, аналог") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                Modifier.horizontalScrollCompat(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Все" to "").plus(types.map { it to it }).forEach { (label, value) ->
                    val active = typeFilter == value
                    Row(
                        Modifier
                            .background(
                                if (active) Amber.copy(alpha = 0.18f) else Panel2,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { typeFilter = value }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Amber else TextDim,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Найдено в базе: ${rows.size} из ${all.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
            )
        }

        items(rows, key = { it.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(12.dp))
                    .clickable { onPick(e) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        e.marking,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        e.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                    Text(
                        "${e.packageName} · аналоги: ${e.equivalents.take(3).joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                        maxLines = 1,
                    )
                }
                Text(
                    e.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cyan,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Горизонтальная прокрутка для чипов типов — без лишних зависимостей. */
    @Composable
    private fun Modifier.horizontalScrollCompat(): Modifier =
        this.then(horizontalScroll(rememberScrollState()))