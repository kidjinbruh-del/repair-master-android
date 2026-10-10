package com.repairmaster.app.data.parts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.text.Charsets

/** Минимальный HTTP-клиент на HttpURLConnection — без внешних библиотек. */
object Http {

    /** Ограничение размера ответа: страница товаров бывает многомегабайтной. */
    private const val MAX_BODY = 6 * 1024 * 1024

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
            readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val raw = conn.inputStream.use { it.readCapped() }
        val encoding = conn.contentEncoding?.lowercase().orEmpty()
        val stream: InputStream = when {
            encoding.contains("gzip") -> GZIPInputStream(raw.inputStream())
            encoding.contains("deflate") -> InflaterInputStream(raw.inputStream())
            else -> raw.inputStream()
        }
        return stream.use { it.readBytes().toString(charsetOf(conn.contentType)) }
    }

    /**
     * Кодировка из Content-Type. Большинство площадок отдают UTF-8, но часть
     * старых магазинов — windows-1251, и тогда без этого вместо названий
     * товара прилетают кракозябры.
     */
    private fun charsetOf(contentType: String?): Charset {
        val name = contentType
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", true) }
            ?.substringAfter('=')
            ?.trim('"', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: return Charsets.UTF_8
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }

    private fun InputStream.readCapped(): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BODY) error("ответ больше $MAX_BODY байт")
            bos.write(buf, 0, n)
        }
        return bos.toByteArray()
    }

    private fun open(url: String, headers: Map<String, String>, timeout: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        conn.instanceFollowRedirects = true
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        )
        conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9")
        if (headers.none { it.key.equals("Accept-Encoding", true) }) {
            conn.setRequestProperty("Accept-Encoding", "gzip")
        }
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    /** Сказать площадке «gzip не нужен» — часть антиботов на него ругается. */
    fun plainHeaders(headers: Map<String, String>): Map<String, String> =
        headers + ("Accept-Encoding" to "identity")

    /**
     * Прогрев сессии: открываем главную страницу площадки и забираем куки.
     * Без этого внутренние эндпоинты отвечают 403/«too many requests».
     * Куки кешируются на несколько минут — иначе каждый поиск тратит лишние
     * десятки секунд на повторный прогрев каждого эндпоинта.
     */
    private val cookieCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    private const val COOKIE_TTL_MS = 5 * 60 * 1000L

    suspend fun warmCookies(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
        val cached = cookieCache[url]
        if (cached != null && System.currentTimeMillis() - cached.first < COOKIE_TTL_MS) {
            return@withContext cached.second
        }
        val cookies = runCatching {
            val conn = open(url, headers, 6000)
            try {
                conn.responseCode
                conn.headerFields.orEmpty()
                    .filterKeys { it != null && it.equals("Set-Cookie", true) }
                    .values.flatten()
                    .mapNotNull { line ->
                        val first = line.substringBefore(';')
                        if (first.contains('=')) first.trim() else null
                    }
                    .joinToString("; ")
            } finally {
                conn.disconnect()
            }
        }.getOrNull().orEmpty()
        // Не кешируем пустой результат: иначе один неудачный прогрев заморозит
        // источник на пять минут без шанса на успех.
        if (cookies.isNotBlank()) cookieCache[url] = System.currentTimeMillis() to cookies
        cookies
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
            ShopLink(
                "ym", "Яндекс Маркет", "marketplace",
                "https://market.yandex.ru/search/?text=$q",
            ),
            ShopLink(
                "ozon", "Ozon", "marketplace",
                "https://www.ozon.ru/search/?text=$q",
            ),
            ShopLink(
                "wb", "Wildberries", "marketplace",
                "https://www.wildberries.ru/catalog/0/search.aspx?search=$q",
            ),
            ShopLink(
                "ali", "AliExpress", "marketplace",
                "https://www.aliexpress.ru/w/wholesale-$slug.html",
            ),
            ShopLink(
                "avito", "Avito", "marketplace",
                "https://www.avito.ru/search?q=$q",
            ),
            ShopLink(
                "mm", "СберМегаМаркет", "marketplace",
                "https://megamarket.ru/catalog/?q=$q",
            ),
            ShopLink(
                "ymarket_all", "Яндекс Маркет — все предложения", "marketplace",
                "https://market.yandex.ru/search?text=$q&how=aprice",
            ),
            ShopLink(
                "dns", "DNS", "electronics",
                "https://www.dns-shop.ru/search/?q=$q",
            ),
            ShopLink(
                "citilink", "Citilink", "electronics",
                "https://www.citilink.ru/search/?text=$q",
            ),
            ShopLink(
                "ekatalog", "E-katalog", "electronics",
                "https://www.e-katalog.ru/search/?q=$q",
            ),
            ShopLink(
                "mvideo", "М.Видео", "electronics",
                "https://www.mvideo.ru/product/search?q=$q",
            ),
            ShopLink(
                "eldorado", "Эльдорадо", "electronics",
                "https://www.eldorado.ru/search/?q=$q",
            ),
            ShopLink(
                "pleer", "Pleer", "electronics",
                "https://www.pleer.ru/search/?what=$q",
            ),
            ShopLink(
                "radcomponents", "Радиокомпоненты", "electronics",
                "https://radcomponents.ru/search/?q=$q",
            ),
            ShopLink(
                "chipdip", "ChipDip", "electronics",
                "https://www.chipdip.ru/search?searchtext=$q",
            ),
            ShopLink(
                "proton", "Протон", "electronics",
                "https://www.proton.ru/search/?q=$q",
            ),
            ShopLink(
                "siberia", "Сибирия", "electronics",
                "https://siberia.ru/search/?q=$q",
            ),
            ShopLink(
                "voltmarket", "VoltMarket", "electronics",
                "https://voltmarket.ru/search/?q=$q",
            ),
            ShopLink(
                "xcom", "Xcom-shop", "electronics",
                "https://www.xcom-shop.ru/search/?q=$q",
            ),
            ShopLink(
                "regard", "Regard", "electronics",
                "https://www.regard.ru/catalog/?search=$q",
            ),
            ShopLink(
                "exist", "Exist (автозапчасти)", "electronics",
                "https://www.exist.ru/search/?q=$q",
            ),
            ShopLink(
                "emex", "Emex (автозапчасти)", "electronics",
                "https://emex.ru/search/?q=$q",
            ),
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
