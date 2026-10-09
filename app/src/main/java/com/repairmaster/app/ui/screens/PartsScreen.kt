package com.repairmaster.app.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.parts.PartOffer
import com.repairmaster.app.data.parts.PartsRepository
import com.repairmaster.app.data.parts.PartsResult
import com.repairmaster.app.data.parts.SourceStatus
import com.repairmaster.app.ui.components.AssetImage
import com.repairmaster.app.ui.components.DataTable
import com.repairmaster.app.ui.components.RemoteImage
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Green
import com.repairmaster.app.ui.theme.Line
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.Red
import com.repairmaster.app.ui.theme.TextDim
import kotlinx.coroutines.launch
import java.util.Locale

private val EXAMPLES = listOf(
    "TOF1006", "1N4007", "KT315", "BC547", "7805",
    "конденсатор 470 мкФ 25В", "NTC 10k", "варистор 14D471K",
    "резистор 0805 10k", "светодиод 5мм 3В", "подшипник 6205",
)

/**
 * Подбор деталей: сначала умный запрос по встроенной базе, затем живой поиск
 * в источниках и быстрые ссылки на площадки. Сеть используется только здесь.
 */
@Composable
fun PartsScreen(
    repo: PartsRepository,
    onOpenUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PartsResult?>(null) }
    var loading by remember { mutableStateOf(false) }

    val imagesOn = repo.isImagesEnabled() && (repo.imageMode() != 2)
    val wifiOnly = repo.imageMode() == 0
    val onWifi = remember(ctx, repo.imageMode()) { isOnWifi(ctx) }

    fun run(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        loading = true
        keyboard?.hide()
        scope.launch {
            val r = repo.search(text)
            result = r
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Подбор деталей",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "Введите маркировку или название детали",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDim,
                        )
                    }
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(Panel, RoundedCornerShape(12.dp))
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Настройки поиска",
                            tint = Amber,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Деталь: TOF1006, 1N4007, конденсатор 470 мкФ…") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Amber)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run(query) }),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .background(Amber, RoundedCornerShape(10.dp))
                            .clickable { run(query) }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    ) {
                        Text(
                            if (loading) "ищем…" else "Найти",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Amber,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        if (!repo.isUnofficialEnabled()) "живые источники выключены" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
            }

            item {
                Column {
                    Text(
                        "ЧАСТО ИЩУТ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EXAMPLES.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { ex ->
                                    Box(
                                        Modifier
                                            .background(Panel2, RoundedCornerShape(8.dp))
                                            .clickable {
                                                query = ex
                                                run(ex)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            ex,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDim,
                                        )
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            val r = result
            if (r != null) {
                item { StatusRow(r.statuses, r.ms) }

                val match = r.dbMatch
                if (match != null) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Panel, RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "НАЙДЕНО В БАЗЕ ДЕТАЛЕЙ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Green,
                            )
                            Text(
                                match.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${match.marking} · ${match.type} · корпус ${match.packageName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Amber,
                            )
                            if (match.params.isNotEmpty()) DataTable(
                                listOf("Параметр", "Значение"),
                                match.params,
                            )
                            if (match.equivalents.isNotEmpty()) {
                                Text(
                                    "Аналоги: ${match.equivalents.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDim,
                                )
                            }
                            if (match.note.isNotBlank()) {
                                Text(
                                    match.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (match.image != null) {
                                Spacer(Modifier.height(4.dp))
                                AssetImage(match.image)
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            "НАЙДЕНО В МАГАЗИНАХ: ${r.offers.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                        )
                        if (r.smartQuery != r.query) {
                            Text(
                                "запрос уточнён базой: «${r.smartQuery}»",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim,
                            )
                        }
                    }
                }

                if (r.offers.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Panel, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        ) {
                            Text(
                                "Живой поиск ничего не вернул. Это нормально: площадки " +
                                    "периодически блокируют запросы из приложений. " +
                                    "Ниже — готовые ссылки, они работают всегда.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDim,
                            )
                        }
                    }
                } else {
                    items(r.offers, key = { it.sourceId + it.url }) { offer ->
                        OfferRow(
                            offer = offer,
                            imagesOn = imagesOn && (!wifiOnly || onWifi),
                            onOpen = { onOpenUrl(offer.url) },
                        )
                    }
                }

                item {
                    Column {
                        Text(
                            "ОТКРЫТЬ ПОИСК В ПЛОЩАДКЕ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cyan,
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.links.forEach { link ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Panel, RoundedCornerShape(10.dp))
                                        .clickable { onOpenUrl(link.url) }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        link.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        when (link.kind) {
                                            "images" -> "фото и datasheet"
                                            "electronics" -> "магазин радиодеталей"
                                            else -> "маркетплейс"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDim,
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

@Composable
private fun OfferRow(offer: PartOffer, imagesOn: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(width = 92.dp, height = 92.dp)) {
            RemoteImage(
                url = offer.imageUrl,
                targetWidthPx = 220,
                enabled = imagesOn,
                modifier = Modifier.fillMaxSize(),
                onTapFull = {},
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                offer.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    offer.priceRub?.let { "${fmtRub(it)} ₽" } ?: "цена не указана",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (offer.inStock) Green else TextDim,
                )
                offer.oldPriceRub?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${fmtRub(it)} ₽",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDim,
                    )
                }
                if (!offer.inStock) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "нет в наличии",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                    )
                }
            }
            Text(
                buildString {
                    append(shopTitle(offer.sourceId))
                    offer.rating?.let { append(" · ★ ${fmtRub(it)}") }
                    offer.reviews?.let { append(" · $it отзывов") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }
    }
}

private fun shopTitle(sourceId: String) = when {
    sourceId.startsWith("wb") -> "Wildberries"
    sourceId.startsWith("ozon") -> "Ozon"
    sourceId.startsWith("avito") -> "Avito"
    sourceId.startsWith("ym") -> "Яндекс Маркет"
    else -> sourceId
}

@Composable
private fun StatusRow(statuses: List<SourceStatus>, ms: Long) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "ИСТОЧНИКИ · ${ms} мс",
            style = MaterialTheme.typography.labelSmall,
            color = Amber,
        )
        statuses.forEach { s ->
            val color = when (s.state) {
                "ok" -> Green
                "empty" -> Amber
                "failed" -> Red
                else -> TextDim
            }
            val mark = when (s.state) {
                "ok" -> "✓"
                "empty" -> "·"
                "failed" -> "✕"
                else -> "—"
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$mark ${s.title}", style = MaterialTheme.typography.bodySmall, color = color)
                Text(
                    when (s.state) {
                        "ok" -> "найдено ${s.found}"
                        "off" -> s.detail
                        else -> s.detail
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
            }
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Amber,
    unfocusedBorderColor = Line,
    focusedLabelColor = Amber,
    unfocusedLabelColor = TextDim,
    cursorColor = Amber,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)

internal fun fmtRub(v: Double): String = String.format(Locale.US, "%.0f", v)

internal fun isOnWifi(ctx: Context): Boolean = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)