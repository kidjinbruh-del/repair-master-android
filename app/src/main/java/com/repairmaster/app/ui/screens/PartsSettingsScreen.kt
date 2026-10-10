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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.repairmaster.app.data.parts.NetworkProbe
import com.repairmaster.app.data.parts.PartsRepository
import com.repairmaster.app.data.parts.PartsSettings
import com.repairmaster.app.data.parts.ProbeResult
import com.repairmaster.app.ui.components.RemoteImages
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Green
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.Red
import com.repairmaster.app.ui.theme.TextDim
import kotlinx.coroutines.launch

/** Настройки подбора деталей: источники, картинки, кэш. */
@Composable
fun PartsSettingsScreen(settings: PartsSettings, repo: PartsRepository) {
    val ctx = LocalContext.current
    var unofficial by remember { mutableStateOf(settings.unofficialEnabled) }
    var imageMode by remember { mutableStateOf(settings.imageMode) }
    var cacheSize by remember { mutableStateOf(RemoteImages.cacheSize(ctx)) }
    var probing by remember { mutableStateOf(false) }
    var probeResults by remember { mutableStateOf<List<ProbeResult>>(emptyList()) }
    val probeScope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Настройки подбора деталей",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        SwitchRow(
            title = "Живой поиск в магазинах",
            subtitle = "Запросы к площадкам: Яндекс Маркет, ChipDip, Wildberries, Ozon, " +
                "Avito. Площадки могут блокировать запросы из приложений и менять свои " +
                "эндпоинты. Выключите, если нужен только справочник и ссылки.",
            value = unofficial,
            onChange = {
                unofficial = it
                settings.unofficialEnabled = it
            },
        )

        Column {
            Text(
                "ЗАГРУЗКА КАРТИНОК",
                style = MaterialTheme.typography.labelSmall,
                color = Cyan,
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "Только по Wi-Fi" to 0,
                "Всегда" to 1,
                "Не загружать" to 2,
            ).forEach { (label, mode) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (imageMode == mode) Amber.copy(alpha = 0.14f) else Panel,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            imageMode = mode
                            settings.imageMode = mode
                            settings.imagesEnabled = mode != 2
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (imageMode == mode) Amber else MaterialTheme.colorScheme.onSurface,
                    )
                    if (imageMode == mode) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium, color = Amber)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text(
                "Кэш картинок: ${cacheSize / 1024 / 1024} МБ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .background(Red.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .clickable {
                        RemoteImages.clearCache(ctx)
                        cacheSize = RemoteImages.cacheSize(ctx)
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text("Очистить кэш", style = MaterialTheme.typography.labelLarge, color = Red)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel2, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text("КАК ЭТО РАБОТАЕТ", style = MaterialTheme.typography.labelSmall, color = Amber)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Встроенная база деталей работает всегда: маркировка → параметры, корпус, аналоги.",
                "Запрос разбирается: «Philips HR1858 ремень» → модель + деталь, из этого " +
                    "строится несколько вариантов поиска.",
                "Живой поиск идёт параллельно в Яндекс Маркет, ChipDip, Wildberries и Ozon.",
                "Запрос уточняется по базе, при полном молчании площадок пробуется " +
                    "следующий вариант запроса.",
                "Если площадка заблокировала запрос — показываем честный статус и ссылку " +
                    "на поиск в браузере.",
                "Картинки грузятся превью-размера и только для видимых строк; полный " +
                    "размер — по тапу.",
                "Весь остальной справочник по-прежнему работает без интернета.",
            ).forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Text(
            "Встроенная база: ${repo.allEntries().size} деталей. " +
                "Чтобы добавить свою — допишите запись в assets/db/parts.json.",
            style = MaterialTheme.typography.labelSmall,
            color = Green,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .background(Panel, RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Text(
                "ПРОВЕРКА ИСТОЧНИКОВ",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Отправляет тестовый запрос на все площадки и показывает, что они ответили. " +
                    "Нужна, если поиск перестал находить товары.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .background(Amber.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                    .clickable {
                        if (probing) return@clickable
                        probing = true
                        probeResults = emptyList()
                        settings.resetHealth()
                        probeScope.launch {
                            val list = NetworkProbe.candidates("1N4007")
                            val out = ArrayList<ProbeResult>(list.size)
                            list.forEach { out += NetworkProbe.run(it) }
                            probeResults = out
                            probing = false
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    if (probing) "проверяем…" else "Проверить площадки",
                    style = MaterialTheme.typography.labelLarge,
                    color = Amber,
                )
            }
            probeResults.forEach { r ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "${if (r.code in 200..299) "✓" else "✕"} ${r.name} — ${r.code}, ${r.length} Б",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (r.code in 200..299) Green else Red,
                )
                Text(
                    r.head,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDim,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .clickable { onChange(!value) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
        }
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .background(
                    if (value) Amber.copy(alpha = 0.28f) else Panel2,
                    RoundedCornerShape(13.dp),
                ),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .background(if (value) Amber else TextDim, RoundedCornerShape(10.dp)),
            )
        }
    }
}