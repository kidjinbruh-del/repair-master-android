package com.repairmaster.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.repairmaster.app.data.ContentBlock
import com.repairmaster.app.data.Warning
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Green
import com.repairmaster.app.ui.theme.Line
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.Red
import com.repairmaster.app.ui.theme.TextDim

@Composable
private fun loadAssetBitmap(path: String): android.graphics.Bitmap? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember(path) { ctx.decodeAssetBitmap(path) }
}

private fun android.content.Context.decodeAssetBitmap(path: String) =
    runCatching {
        assets.open(path).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

/** Иллюстрация из assets/img (сгенерирована заранее, офлайн). */
@Composable
fun AssetImage(
    path: String,
    modifier: Modifier = Modifier,
) {
    val bmp = loadAssetBitmap(path)
    if (bmp == null) {
        Box(
            modifier
                .fillMaxWidth()
                .background(Panel2, RoundedCornerShape(12.dp)),
        ) {
            Text(
                "нет изображения: $path",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                modifier = Modifier.padding(10.dp),
            )
        }
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Transparent, RoundedCornerShape(12.dp)),
        )
    }
}

/** Кликабельная иллюстрация: тап открывает полноэкранный просмотр с зумом. */
@Composable
fun AssetImageBox(
    path: String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.clickable { open = true }) {
        AssetImage(path)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "⤢",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
    if (open) {
        FullscreenImage(path) { open = false }
    }
}

/** Полноэкранный просмотр: щипок-масштаб, перетаскивание, двойной тап — сброс. */
@Composable
fun FullscreenImage(path: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        },
                        onTap = { onDismiss() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = loadAssetBitmap(path)
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("✕", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "сводка щипком · двойной тап — сброс",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            )
        }
    }
}

@Composable
fun WarningBox(warning: Warning) {
    val (color, icon) = when (warning.level) {
        "danger" -> Red to Icons.Filled.ErrorOutline
        "info" -> Cyan to Icons.Filled.Info
        else -> Amber to Icons.Filled.Warning
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color,
            modifier = Modifier.size(20.dp))
        Text(
            warning.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Таблица с горизонтальной прокруткой (для широких справочников). */
@Composable
fun DataTable(
    headers: List<String>,
    rows: List<List<String>>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel2, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState()),
    ) {
        if (headers.any { it.isNotBlank() }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Line.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp, horizontal = 10.dp),
            ) {
                headers.forEach { h ->
                    Text(
                        h,
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber,
                        modifier = Modifier.padding(end = 22.dp),
                    )
                }
            }
        }
        rows.forEachIndexed { idx, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp, horizontal = 10.dp),
            ) {
                row.forEachIndexed { ci, cell ->
                    Text(
                        cell,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ci == 0) MaterialTheme.colorScheme.onSurface else TextDim,
                        modifier = Modifier.padding(end = 22.dp),
                    )
                }
            }
            if (idx != rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Line.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

@Composable
fun ContentBlockView(block: ContentBlock) {
    when (block) {
        is ContentBlock.Steps -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (block.heading.isNotBlank()) {
                Text(
                    block.heading,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            block.items.forEachIndexed { i, text ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(Amber.copy(alpha = 0.16f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                        )
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        is ContentBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (block.heading.isNotBlank()) {
                Text(
                    block.heading,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            DataTable(block.headers, block.rows)
        }
    }
}

/** Плашка сложности проблемы. */
@Composable
fun SeverityBadge(severity: String) {
    val (label, color) = when (severity) {
        "critical" -> "Критично" to Red
        "high" -> "Высокая" to Amber
        "medium" -> "Средняя" to Cyan
        else -> "Низкая" to Green
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}