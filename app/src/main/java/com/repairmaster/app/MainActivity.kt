package com.repairmaster.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.repairmaster.app.data.ContentRepository
import com.repairmaster.app.ui.screens.CategoryScreen
import com.repairmaster.app.ui.screens.HomeScreen
import com.repairmaster.app.ui.screens.ItemScreen
import com.repairmaster.app.ui.screens.ProblemScreen
import com.repairmaster.app.ui.screens.SearchScreen
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.RepairMasterTheme

private sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Category(val id: String) : Screen
    data class Item(val id: String) : Screen
    data class Problem(val id: String) : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val catalog = ContentRepository(this).load()
        setContent {
            RepairMasterTheme(dark = true) {
                AppRoot(catalog)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(catalog: ContentRepository.Catalog) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val title = when (val s = current) {
        Screen.Home -> "Мастер по ремонту"
        Screen.Search -> "Поиск по справочнику"
        is Screen.Category ->
            catalog.categories.firstOrNull { it.id == s.id }?.title ?: "Раздел"
        is Screen.Item ->
            catalog.categories.firstOrNull { c -> c.items.any { it.id == s.id } }
                ?.items?.firstOrNull { it.id == s.id }?.title ?: "Тема"
        is Screen.Problem ->
            catalog.problems.firstOrNull { it.id == s.id }?.title ?: "Инструкция"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Panel,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    if (stack.size > 1) {
                        IconButton(onClick = { stack.removeAt(stack.lastIndex) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Amber,
                            )
                        }
                    }
                },
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                },
                actions = {
                    if (stack.size == 1) {
                        IconButton(onClick = { stack.add(Screen.Search) }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Поиск",
                                tint = Amber,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (val s = current) {
                Screen.Home -> HomeScreen(
                    categories = catalog.categories,
                    problemCount = catalog.problems.size,
                    onCategory = { stack.add(Screen.Category(it)) },
                    onProblem = { stack.add(Screen.Problem(it)) },
                    onSearch = { stack.add(Screen.Search) },
                )

                Screen.Search -> SearchScreen(
                    categories = catalog.categories,
                    onProblem = { stack.add(Screen.Problem(it)) },
                    onCategory = { stack.add(Screen.Category(it)) },
                )

                is Screen.Category -> {
                    val cat = catalog.categories.firstOrNull { it.id == s.id }
                    if (cat == null) {
                        EmptyState("Раздел не найден")
                    } else {
                        CategoryScreen(
                            category = cat,
                            onItem = { stack.add(Screen.Item(it)) },
                            onProblem = { stack.add(Screen.Problem(it)) },
                        )
                    }
                }

                is Screen.Item -> {
                    val item = catalog.categories
                        .flatMap { it.items }
                        .firstOrNull { it.id == s.id }
                    if (item == null) {
                        EmptyState("Тема не найдена")
                    } else {
                        ItemScreen(item, onProblem = { stack.add(Screen.Problem(it)) })
                    }
                }

                is Screen.Problem -> {
                    val problem = catalog.problems.firstOrNull { it.id == s.id }
                    if (problem == null) {
                        EmptyState("Инструкция не найдена")
                    } else {
                        ProblemScreen(problem)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}