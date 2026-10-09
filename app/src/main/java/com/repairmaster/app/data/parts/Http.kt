package com.repairmaster.app.data.parts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

/** Минимальный HTTP-клиент на HttpURLConnection — без внешних библиотек. */
object Http {

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        timeout: Int = 12000,
    ): String = withContext(Dispatchers.IO) {
        val conn = open(url, headers, timeout)
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val raw = conn.inputStream.use { it.readBytes() }
            val encoding = conn.contentEncoding?.lowercase().orEmpty()
            if (encoding.contains("gzip")) {
                GZIPInputStream(raw.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
            } else {
                raw.toString(Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, headers: Map<String, String>, timeout: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36",
        )
        conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    /**
     * Прогрев сессии: открываем главную страницу площадки и забираем куки.
     * Без этого внутренние эндпоинты отвечают 403/«too many requests».
     */
    suspend fun warmCookies(url: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(url, emptyMap(), 12000)
            try {
                conn.responseCode
                val raw = conn.headerFields.filterKeys { it != null }
                    .filterKeys { it!!.equals("Set-Cookie", true) }
                    .values.flatten()
                raw.mapNotNull { line ->
                    val first = line.substringBefore(';')
                    if (first.contains('=')) first.trim() else null
                }.joinToString("; ")
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
    }

    private fun readAll(stream: java.io.InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        stream.copyTo(bos)
        return bos.toByteArray()
    }
}

/** Ссылки на поиск: работают всегда, независимо от сети и антибот-защиты. */
object ShopLinks {

    fun all(query: String): List<ShopLink> {
        val q = Http.enc(query)
        val slug = query.trim().lowercase()
            .replace(Regex("[^a-zа-я0-9]+"), "-")
            .trim('-')
            .ifEmpty { "part" }
        return listOf(
            ShopLink("ozon", "Ozon", "marketplace", "https://www.ozon.ru/search/?text=$q"),
            ShopLink(
                "wb", "Wildberries", "marketplace",
                "https://www.wildberries.ru/catalog/0/search.aspx?search=$q",
            ),
            ShopLink(
                "ali", "AliExpress", "marketplace",
                "https://www.aliexpress.ru/w/wholesale-$slug.html",
            ),
            ShopLink("avito", "Avito", "marketplace", "https://www.avito.ru/search/?q=$q"),
            ShopLink(
                "ym", "Яндекс Маркет", "marketplace",
                "https://market.yandex.ru/search/?text=$q",
            ),
            ShopLink("dns", "DNS", "electronics", "https://www.dns-shop.ru/search/?q=$q"),
            ShopLink("citilink", "Citilink", "electronics", "https://www.citilink.ru/search/?q=$q"),
            ShopLink("ekatalog", "E-katalog", "electronics", "https://www.e-katalog.ru/search/?q=$q"),
            ShopLink("pleer", "Pleer", "electronics", "https://www.pleer.ru/search/?what=$q"),
            ShopLink("radcomponents", "Радиокомпоненты", "electronics", "https://radcomponents.ru/search/?q=$q"),
            ShopLink(
                "yandex_img", "Яндекс Картинки", "images",
                "https://yandex.ru/images/search?text=${Http.enc("$query datasheet")}",
            ),
            ShopLink(
                "google_img", "Google Картинки", "images",
                "https://www.google.com/search?tbm=isch&q=$q",
            ),
        )
    }
}