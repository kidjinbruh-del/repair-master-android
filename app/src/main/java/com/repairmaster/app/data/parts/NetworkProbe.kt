package com.repairmaster.app.data.parts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Один пробуемый адрес. */
data class Probe(
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val dumpTo: String? = null,
)

data class ProbeResult(
    val name: String,
    val code: Int,
    val length: Int,
    val head: String,
)

/**
 * Диагностика источников: честно показывает, что площадка ответила.
 * Нужна, потому что внутренние эндпоинты маркетплейсов меняются без предупреждения.
 */
object NetworkProbe {

    private const val TAG = "PartsProbe"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    private val wbHeaders = mapOf(
        "Accept" to "*/*",
        "Origin" to "https://www.wildberries.ru",
        "Referer" to "https://www.wildberries.ru/",
    )

    private val ozonHeaders = mapOf(
        "Accept" to "*/*",
        "x-o3-app-name" to "dapi_client",
        "x-o3-app-version" to "release_17000000",
        "Origin" to "https://www.ozon.ru",
        "Referer" to "https://www.ozon.ru/",
    )

    fun candidates(query: String): List<Probe> {
        val q = Http.enc(query)
        val wbQuery = "appType=1&curr=rub&dest=-1257786&query=$q" +
            "&resultset=catalog&limit=10&sort=popular&spp=30"
        return listOf(
            Probe(
                "WB composer",
                "https://search.wb.ru/exactmatch/ru/common/v13/search?$wbQuery",
                wbHeaders,
            ),
            Probe(
                "Ozon composer",
                "https://www.ozon.ru/api/composer-api.bx/page/json/v2" +
                    "?url=%2Fsearch%2F%3Ftext%3D$q%26from_global%3Dtrue",
                ozonHeaders,
            ),
            Probe("Ozon страница", "https://www.ozon.ru/search/?text=$q"),
            Probe("Citilink поиск", "https://www.citilink.ru/search/?q=$q"),
            Probe("E-katalog поиск", "https://www.e-katalog.ru/search/?q=$q"),
            Probe("Pleer поиск", "https://www.pleer.ru/search/?what=$q"),
            Probe("AliExpress поиск", "https://www.aliexpress.ru/wholesale?SearchText=$q"),
            Probe("Avito поиск", "https://www.avito.ru/search/?q=$q"),
            Probe("DNS страница", "https://www.dns-shop.ru/search/?q=$q"),
            Probe("Яндекс Маркет", "https://market.yandex.ru/search/?text=$q", dumpTo = "/sdcard/ym.html"),
            Probe(
                "DNS api",
                "https://www.dns-shop.ru/search/api/v1/search?query=$q&city=%D0%9C%D0%BE%D1%81%D0%BA%D0%B2%D0%B0",
                mapOf("Accept" to "application/json"),
            ),
        )
    }

    suspend fun run(probe: Probe, dumpFile: java.io.File? = null): ProbeResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(probe.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
                    probe.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                    val compact = body.replace(Regex("\\s+"), " ").trim().take(140)
                    Log.i(TAG, "${probe.name}: $code, ${body.length} байт, $compact")
                    probe.dumpTo?.let { path ->
                        dumpFile?.let { f ->
                            runCatching {
                                f.writeText(body, Charsets.UTF_8)
                                Log.i(TAG, "dump → ${f.absolutePath} ($path)")
                            }
                        }
                    }
                    ProbeResult(probe.name, code, body.length, compact)
                } finally {
                    conn.disconnect()
                }
            }.getOrElse {
                val res = ProbeResult(
                    probe.name, -1, 0,
                    it.javaClass.simpleName + ": " + (it.message ?: "без ответа"),
                )
                Log.w(TAG, "${probe.name}: СБОЙ ${res.head}")
                res
            }
        }
}