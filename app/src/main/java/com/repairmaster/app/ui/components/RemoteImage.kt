package com.repairmaster.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Загрузка картинок из сети «по потребности»: в списке грузится только превью
 * нужного размера и только то, что реально видно. Полный размер — по тапу.
 * Кэш двухуровневый: в памяти (LruCache) и на диске в cacheDir с ограничением
 * размера, поэтому приложение не разрастается и не качает лишнее.
 */
object RemoteImages {

    private const val DISK_LIMIT = 96L * 1024 * 1024   // 96 МБ
    private const val TIMEOUT = 15000

    private val memory = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4096)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "parts_img").apply { mkdirs() }

    private fun key(url: String) = MessageDigest.getInstance("MD5")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun cacheSize(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun clearCache(ctx: Context) {
        memory.evictAll()
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Обрезает кэш до лимита: удаляются самые старые файлы. */
    private fun trim(ctx: Context) {
        val files = dir(ctx).listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= DISK_LIMIT) break
            total -= f.length()
            f.delete()
        }
    }

    suspend fun load(ctx: Context, url: String, targetPx: Int): Bitmap? {
        memory.get(url)?.let { return it }
        val file = File(dir(ctx), key(url))
        if (file.exists() && file.length() > 0) {
            decodeFile(file, targetPx)?.let {
                memory.put(url, it)
                return it
            }
        }
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                    setRequestProperty("User-Agent", PartsUserAgent.VALUE)
                    setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                }
                try {
                    if (conn.responseCode !in 200..299) return@runCatching null
                    conn.inputStream.use { it.readBytes() }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        } ?: return null

        val bmp = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { full ->
                val scaled = scaleTo(full, targetPx)
                if (scaled !== full) full.recycle()
                scaled
            }
        } ?: return null

        runCatching {
            withContext(Dispatchers.IO) {
                File(dir(ctx), key(url)).writeBytes(bytes)
                trim(ctx)
            }
        }
        memory.put(url, bmp)
        return bmp
    }

    private fun decodeFile(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(bounds.outWidth, targetPx)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.path, opts)
    }

    private fun scaleTo(src: Bitmap, targetPx: Int): Bitmap {
        if (src.width <= targetPx) return src
        val h = (src.height.toLong() * targetPx / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, targetPx, h, true)
    }

    private fun sampleFor(width: Int, targetPx: Int): Int {
        var sample = 1
        var w = width
        while (w / 2 >= targetPx) {
            w /= 2
            sample *= 2
        }
        return sample
    }
}

/** Один User-Agent на всё приложение, чтобы источники не видели разнобой. */
object PartsUserAgent {
    const val VALUE =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"
}

/** Превью из сети: грузится лениво, по тапу открывается полный размер. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 320,
    enabled: Boolean = true,
    onTapFull: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var bmp by remember(url, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }

    if (url != null && enabled) {
        androidx.compose.runtime.LaunchedEffect(url, targetWidthPx) {
            bmp = null
            failed = false
            val got = RemoteImages.load(ctx, url, targetWidthPx)
            bmp = got
            failed = got == null
        }
    }

    Box(
        modifier
            .background(Panel2, RoundedCornerShape(10.dp))
            .then(if (onTapFull != null && url != null) Modifier.clickable { full = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val image = bmp
        when {
            image != null -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )

            failed -> Icon(
                Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.padding(10.dp),
            )

            else -> Text(
                if (enabled) "…" else "картинки выключены",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = TextDim,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    if (full && url != null) {
        RemoteImageDialog(url) { full = false }
    }
}

/** Полноэкранный просмотр картинки из сети: щипок-масштаб, двойной тап — сброс. */
@Composable
private fun RemoteImageDialog(url: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var bmp by remember(url) { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(url) {
        scope.launch { bmp = RemoteImages.load(ctx, url, 1280) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            dx += pan.x
                            dy += pan.y
                        } else {
                            dx = 0f
                            dy = 0f
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { scale = 1f; dx = 0f; dy = 0f },
                        onTap = { onDismiss() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val image = bmp
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = dx
                            translationY = dy
                        },
                )
            } else {
                Text("загрузка…", color = Color.White.copy(alpha = 0.7f))
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "✕",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "сводка щипком · двойной тап — сброс",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
            )
        }
    }
}