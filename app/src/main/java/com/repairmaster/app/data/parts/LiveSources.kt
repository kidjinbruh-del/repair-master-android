package com.repairmaster.app.data.parts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Один эндпоинт источника. Площадки меняют адреса без предупреждения, поэтому
 * источник перебирает свои варианты по порядку: сработал хоть один — поиск удался.
 */
data class SourceAttempt(
    val url: (String) -> String,
    val parse: (String, Int) -> List<PartOffer>,
    val headers: Map<String, String> = emptyMap(),
    /** Главная страница для прогрева куки, если эндпоинт этого требует. */
    val warm: String? = null,
    val timeout: Int = 12000,
)

/**
 * Источник на [SourceAttempt]-ах: сам перебирает варианты, сам решает, когда
 * отдавать пустой результат, а когда бросать исключение.
 *
 * Важно для статуса в интерфейсе: если площадка ответила, но товаров не нашлось,
 * возвращается пустой список («ничего не найдено»), а не ошибка.
 */
abstract class HttpSource(
    override val id: String,
    override val title: String,
    override val unofficial: Boolean,
    protected val attempts: List<SourceAttempt>,
) : PartsSource {

    /** Всегда доступная ссылка: открывается в браузере без всякого парсинга. */
    override fun searchUrl(query: String): String = attempts.first().url(query)

    override suspend fun search(query: String, limit: Int): List<PartOffer> {
        // Жёсткий дедлайн на источник: без него два «прогретых» эндпоинта с
        // таймаутом 7с легко растягивают поиск до сорока секунд.
        val deadlineMs = attempts.sumOf { it.timeout }.coerceAtMost(15000).toLong()
        return try {
            withTimeout(deadlineMs) { runAttempts(query, limit) }
        } catch (e: TimeoutCancellationException) {
            error("$title: не ответил за ${deadlineMs / 1000} с")
        }
    }

    /**
     * Перебирает эндпоинты по порядку. Пустой список здесь значит «площадка
     * ответила, но товаров нет» — это честный результат, а не ошибка.
     */
    private suspend fun runAttempts(query: String, limit: Int): List<PartOffer> {
        var lastError: Throwable? = null
        var answered = false
        for (attempt in attempts) {
            try {
                val cookies = attempt.warm?.let { Http.warmCookies(it, attempt.headers) }
                val headers = buildMap {
                    putAll(attempt.headers)
                    if (!cookies.isNullOrBlank()) put("Cookie", cookies)
                }
                val body = Http.get(attempt.url(query), headers, attempt.timeout)
                val offers = attempt.parse(body, limit)
                if (offers.isNotEmpty()) return offers
                answered = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                lastError = t
            }
        }
        if (answered && lastError == null) return emptyList()
        throw lastError ?: error("$title: нет доступных эндпоинтов")
    }
}

/** Сеть/антибот: имеет смысл повторить запрос. */
internal fun isRetryable(t: Throwable): Boolean {
    val m = t.message.orEmpty()
    return m.contains("HTTP 403") || m.contains("HTTP 429") ||
        m.contains("HTTP 5") || m.contains("timeout", true) ||
        t is java.net.SocketTimeoutException || t is java.io.InterruptedIOException
}

// ---------------------------------------------------------------------------
// Разбор ответов
// ---------------------------------------------------------------------------

/** JSON-сущности: `&#160;` и `&#xNN;`, которые не покрываются простым replace. */
internal val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)

internal fun decodeNumericEntities(s: String): String =
    NUMERIC_ENTITY.replace(s) { m ->
        val hex = m.groupValues[1].equals("x", true)
        val code = m.groupValues[2].toIntOrNull(if (hex) 16 else 10)
        if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
    }

/**
 * Разбор Schema.org из HTML. Это самый устойчивый способ: страницы поиска
 * Яндекс Маркета, многих магазинов и каталогов отдают список товаров как
 * `ItemList` из `Product`, и редизайн вёрстки его не ломает.
 */
internal object JsonLdParser {

    private val LD = Regex(
        "(?is)<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
    )

    fun parse(html: String, limit: Int, sourceId: String, shop: String): List<PartOffer> {
        val out = ArrayList<PartOffer>()
        val seen = HashSet<String>()
        for (block in LD.findAll(html)) {
            val json = unescape(block.groupValues[1]) ?: continue
            val root = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val products = ArrayList<JSONObject>()
            collect(root, products)
            for (p in products) {
                if (out.size >= limit) break
                val offer = mapOffer(p, sourceId, shop, out.size) ?: continue
                val key = offer.url + "|" + offer.title
                if (!seen.add(key)) continue
                out += offer
            }
        }
        return out
    }

    private fun unescape(raw: String): String? {
        var s = raw.trim()
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .let { decodeNumericEntities(it) }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        s = s.substring(start, end + 1)
        return s.ifBlank { null }
    }

    /** Рекурсивно собираем все Product: список может быть вложен куда угодно. */
    private fun collect(node: Any?, out: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                if (node.optString("@type").equals("Product", true) &&
                    node.optString("name").isNotBlank()
                ) {
                    out += node
                }
                node.keys().forEach { key -> collect(node.opt(key), out) }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
        }
    }

    private fun mapOffer(p: JSONObject, sourceId: String, shop: String, index: Int): PartOffer? {
        val title = p.optString("name").trim()
        if (title.isBlank()) return null
        val offers = when (val o = p.opt("offers")) {
            is JSONObject -> o
            is JSONArray -> o.optJSONObject(0)
            else -> null
        }
        val price = offers?.num("price") ?: offers?.num("lowPrice") ?: p.num("price")
        val url = firstNotBlank(
            offers?.optString("url"),
            p.optString("url"),
            p.optString("@id"),
        ) ?: return null
        val rating = p.optJSONObject("aggregateRating")
        val availability = offers?.optString("availability").orEmpty()
        val image = when (val im = p.opt("image")) {
            is String -> im
            is JSONArray -> im.optString(0)
            else -> null
        }?.takeIf { it.isNotBlank() && it.startsWith("http") }
        val reviews = rating?.num("ratingCount") ?: rating?.num("reviewCount")
        return PartOffer(
            sourceId = "$sourceId-$index-${p.optString("sku").ifBlank { url.takeLast(12) }}",
            shop = shop,
            title = title,
            priceRub = price,
            url = url,
            imageUrl = image,
            rating = rating?.num("ratingValue"),
            reviews = reviews?.toInt()?.takeIf { it > 0 },
            inStock = !availability.contains("OutOfStock"),
        )
    }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() && it.startsWith("http") }
}

/** Цена в JSON бывает и числом, и строкой — читаем оба варианта. */
internal fun JSONObject.num(key: String): Double? {
    if (isNull(key)) return null
    optString(key).toDoubleOrNull()?.let { if (it > 0) return it }
    return optDouble(key).takeIf { !it.isNaN() && it > 0 }
}

/** Разбор вёрстки ChipDip: детали, куда больше нигде не зайти. */
internal object ChipDipParser {

    // Точное имя класса: product_row, но НЕ product_row_controls и не -prices.
    private val ROW = Regex("(?i)<div[^>]*class=\"product_row(?:\\s[^\"]*)?\"[^>]*>")
    private val LINK = Regex("(?is)<a[^>]+href=\"([^\"]+)\"")
    private const val PRODUCT_PREFIX = "/product"

    fun parse(html: String, limit: Int, base: String): List<PartOffer> {
        val marks = ROW.findAll(html).toList()
        val out = ArrayList<PartOffer>()
        for ((index, mark) in marks.withIndex()) {
            if (out.size >= limit) break
            val start = mark.range.last + 1
            val end = marks.getOrNull(index + 1)?.range?.first ?: html.length
            if (end <= start) continue
            val chunk = html.substring(start, end)
            val link = LINK.find(chunk)?.groupValues?.get(1) ?: continue
            if (!link.startsWith(PRODUCT_PREFIX)) continue
            val nameBlock = Regex("""(?is)<div class="name">(.*?)</div>""").find(chunk)
                ?: continue
            val title = stripTags(nameBlock.groupValues[1])
            if (title.isBlank()) continue
            val price = priceOf(chunk)
            val image = Regex("""(?is)<img[^>]+src="([^"]+)"""")
                .find(chunk)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() && !it.contains("noimage") }
            val inStock = chunk.contains("item__avail_available")
            out += PartOffer(
                sourceId = "chipdip-${out.size}-${link.takeLast(14)}",
                shop = "ChipDip",
                title = title,
                priceRub = price,
                url = base + link,
                imageUrl = image,
                inStock = inStock,
            )
        }
        return out
    }

    /**
     * Цена приходит с разными неразрывными пробелами («4 810», «12 960»), поэтому
     * просто выкидываем всё, кроме цифр, точки и запятой: иначе цена 4810 ₽
     * превращается в 4 ₽. HTML-сущности раскрываем заранее — в разметке стоит
     * `&#160;`, и без этого его цифры приклеиваются к цене (4810 → 4160810).
     */
    private fun priceOf(chunk: String): Double? {
        val block = Regex("""(?is)<div class="price">(.*?)</div>""").find(chunk) ?: return null
        val raw = stripTags(block.groupValues[1])
        if (raw.isBlank()) return null
        val cleaned = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (cleaned.isBlank()) return null
        val m = Regex("""\d+(?:[.,]\d+)?""").find(cleaned) ?: return null
        return m.value.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun stripTags(s: String): String =
        s.replace(Regex("(?s)<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .let { decodeNumericEntities(it) }
            // Неразрывные и тонкие пробелы — обычным replace не берутся.
            .replace(Regex("[\\s\\u00a0\\u2009\\u200a\\u202f\\u2007\\ufeff]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

/**
 * Универсальный разбор JSON-магазинов: Wildberries и Ozon отдают товары
 * вложенными объектами, и формат у них плавает. Держим все встречавшиеся
 * варианты полей, чтобы смена версии API не убивала поиск сразу.
 */
internal object JsonApiParser {

    fun parse(
        root: JSONObject,
        limit: Int,
        sourceId: String,
        shop: String,
        urlPrefix: String = "",
        fallbackUrl: ((JSONObject) -> String?)? = null,
    ): List<PartOffer> {
        val items = findItems(root)
        val out = ArrayList<PartOffer>()
        for (i in 0 until items.length()) {
            if (out.size >= limit) break
            val raw = items.optJSONObject(i) ?: continue
            // Ozon заворачивает товар в mainState, остальные отдают его сразу.
            val p = raw.optJSONObject("mainState") ?: raw
            val title = pick(p, "name", "title", "productName").trim()
            if (title.isBlank()) continue
            val price = priceOf(p)
            val link = pick(p, "link", "url", "productUrl")
            val image = imageOf(p)
            val rating = p.optJSONObject("rating") ?: p.optJSONObject("reviewRating")
            val url = when {
                link.isNotBlank() && link.startsWith("http") -> link
                link.isNotBlank() && link.startsWith("/") -> urlPrefix + link
                else -> fallbackUrl?.invoke(p)
            } ?: continue
            out += PartOffer(
                sourceId = "$sourceId-$i",
                shop = shop,
                title = title,
                priceRub = price,
                url = url,
                imageUrl = image,
                rating = rating?.num("ratingValue") ?: rating?.num("average")
                    ?: p.num("rating")?.takeIf { it in 0.1..5.0 },
                reviews = (rating?.num("ratingCount") ?: p.optJSONObject("feedbacks")
                    ?.num("total"))?.toInt()?.takeIf { it > 0 },
                inStock = inStock(p),
            )
        }
        return out
    }

    /** Ищем массив товаров: он лежит под разными ключами в разных версиях API. */
    private fun findItems(root: JSONObject): JSONArray {
        val preferred = listOf("products", "items", "data", "results", "goods")
        for (key in preferred) {
            val v = root.opt(key) ?: root.optJSONObject("data")?.opt(key)
            if (v is JSONArray && v.length() > 0) return v
            if (v is JSONObject) {
                val inner = v.optJSONArray("items") ?: v.optJSONArray("products")
                if (inner != null && inner.length() > 0) return inner
            }
        }
        return searchArray(root, 0)
    }

    private fun searchArray(node: Any?, depth: Int): JSONArray {
        if (depth > 4 || node !is JSONObject) return JSONArray()
        node.keys().forEach { key ->
            when (val v = node.opt(key)) {
                is JSONArray -> if (looksLikeProducts(v)) return v
                is JSONObject -> {
                    val inner = searchArray(v, depth + 1)
                    if (inner.length() > 0) return inner
                }
            }
        }
        return JSONArray()
    }

    private fun looksLikeProducts(a: JSONArray): Boolean {
        for (i in 0 until minOf(a.length(), 3)) {
            val o = a.optJSONObject(i) ?: continue
            if (pick(o, "name", "title").isNotBlank()) return true
        }
        return false
    }

    /**
     * Цена в JSON бывает в рублях («199.00») и в копейсках (`priceU`, WB `sizes`).
     * Различаем по имени поля, а не по величине: иначе 4 ₽ превращаются в 400.
     */
    private fun priceOf(p: JSONObject): Double? {
        val sizes = p.opt("sizes")
        val fromSizes = when (sizes) {
            is JSONArray -> sizes.optJSONObject(0)?.num("price")
            is JSONObject -> sizes.optJSONObject("0")?.num("price")
            else -> null
        }
        // Явные копейки.
        p.num("priceU")?.let { return it / 100.0 }
        fromSizes?.let { return it / 100.0 }
        val priceObj = p.optJSONObject("price")
        if (priceObj != null) {
            for (key in listOf("price", "finalPrice", "value", "priceMarkdown")) {
                val raw = priceObj.optString(key).takeIf { it.isNotBlank() } ?: continue
                val hasPoint = raw.contains('.') || raw.contains(',')
                val v = raw.replace(',', '.').toDoubleOrNull() ?: continue
                if (v <= 0) continue
                return when {
                    hasPoint -> v
                    v >= 50000 -> v / 100.0
                    else -> v
                }
            }
            val v = priceObj.num("price") ?: priceObj.num("finalPrice") ?: priceObj.num("value")
            if (v != null) return if (v >= 50000) v / 100.0 else v
        }
        val flat = p.num("price") ?: p.num("finalPrice") ?: p.num("value") ?: return null
        return if (flat >= 50000) flat / 100.0 else flat
    }

    private fun imageOf(p: JSONObject): String? {
        val direct = pick(p, "image", "imageUrl", "image_url")
        val img = p.optJSONObject("image")
        val nested = img?.optJSONArray("items")?.optJSONObject(0)?.optString("link")
            ?: img?.optString("link")
        val list = p.optJSONArray("images")?.optJSONObject(0)?.optString("link")
        return listOf(direct, nested, list).firstOrNull {
            !it.isNullOrBlank() && it.startsWith("http")
        }
    }

    private fun inStock(p: JSONObject): Boolean {
        val stock = p.optInt("totalQuantity", -1)
        if (stock >= 0) return stock > 0
        val sizes = p.optJSONArray("sizes")?.optJSONObject(0)
        val st = sizes?.optJSONArray("stocks")
        if (st != null && st.length() > 0) {
            return (0 until st.length()).any { st.optJSONObject(it)?.optInt("qty", 0) ?: 0 > 0 }
        }
        return true
    }

    private fun pick(p: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { k -> p.optString(k).takeIf { it.isNotBlank() } } ?: ""
}

// ---------------------------------------------------------------------------
// Источники
// ---------------------------------------------------------------------------

/**
 * Яндекс Маркет: страница поиска содержит Schema.org (ItemList) — название,
 * цена, фото, рейтинг и ссылка. Самый устойчивый канал из крупных площадок.
 * Вторым эндпоинтом пробуем сортировку по цене: в выдачу попадают другие товары.
 */
class YandexMarketSource : HttpSource(
    id = "ym",
    title = "Яндекс Маркет",
    unofficial = false,
    attempts = listOf(
        SourceAttempt(
            url = { q -> "https://market.yandex.ru/search/?text=${Http.enc(q)}" },
            parse = { body, limit ->
                JsonLdParser.parse(body, limit, "ym", "Яндекс Маркет")
            },
            timeout = 15000,
        ),
        SourceAttempt(
            url = { q -> "https://market.yandex.ru/search?text=${Http.enc(q)}&how=aprice" },
            parse = { body, limit ->
                JsonLdParser.parse(body, limit, "ym", "Яндекс Маркет")
            },
            timeout = 15000,
        ),
    ),
)

/** ChipDip: вёрстка со строками `product_row` — цены, наличие, фото, корпуса. */
class ChipDipSource : HttpSource(
    id = "chipdip",
    title = "ChipDip",
    unofficial = false,
    attempts = listOf(
        SourceAttempt(
            url = { q -> "https://www.chipdip.ru/search?searchtext=${Http.enc(q)}" },
            parse = { body, limit -> ChipDipParser.parse(body, limit, "https://www.chipdip.ru") },
            timeout = 15000,
        ),
    ),
)

/**
 * Неофициальный источник: внутренний эндпоинт поиска Wildberries.
 * Формат ответа меняется — при неудаче источник честно сообщает статус.
 */
class WildberriesSource : HttpSource(
    id = "wb",
    title = "Wildberries",
    unofficial = true,
    attempts = listOf(
        SourceAttempt(
            url = { q ->
                "https://search.wb.ru/exactmatch/ru/common/v7/search" +
                    "?appType=1&curr=rub&dest=-1257786&query=${Http.enc(q)}" +
                    "&resultset=catalog&limit=30&sort=popular&spp=30&suppressSpellcheck=false"
            },
            parse = { body, limit ->
                JsonApiParser.parse(
                    JSONObject(body),
                    limit,
                    "wb",
                    "Wildberries",
                    fallbackUrl = { p ->
                        p.optLong("id", 0L)
                            .takeIf { it > 0 }
                            ?.let { "https://www.wildberries.ru/catalog/$it/detail.aspx" }
                    },
                )
            },
            headers = mapOf("Accept" to "*/*"),
            warm = "https://www.wildberries.ru/",
            timeout = 7000,
        ),
        SourceAttempt(
            url = { q ->
                "https://search.wb.ru/exactmatch/ru/common/v5/search" +
                    "?appType=1&curr=rub&dest=-1257786&query=${Http.enc(q)}" +
                    "&resultset=catalog&limit=30"
            },
            parse = { body, limit ->
                JsonApiParser.parse(
                    JSONObject(body),
                    limit,
                    "wb",
                    "Wildberries",
                    fallbackUrl = { p ->
                        p.optLong("id", 0L)
                            .takeIf { it > 0 }
                            ?.let { "https://www.wildberries.ru/catalog/$it/detail.aspx" }
                    },
                )
            },
            warm = "https://www.wildberries.ru/",
            timeout = 7000,
        ),
    ),
)

/**
 * Неофициальный источник: внутренний composer-API Ozon.
 * Ответ приходит вложенным JSON-строкой в widgetStates — распаковываем.
 */
class OzonSource : HttpSource(
    id = "ozon",
    title = "Ozon",
    unofficial = true,
    attempts = listOf(
        SourceAttempt(
            url = { q ->
                "https://www.ozon.ru/api/composer-api.bx/page/json/v2" +
                    "?url=/search/?text=${Http.enc(q)}&from_global=true&sorting=rating"
            },
            parse = { body, limit ->
                val widgets = JSONObject(body).optJSONObject("widgetStates")
                    ?: error("Ozon: нет widgetStates")
                val key = widgets.keys().asSequence()
                    .firstOrNull { it.startsWith("searchResultsV2") }
                    ?: error("Ozon: не найден блок поиска")
                val inner = JSONObject(widgets.optString(key))
                JsonApiParser.parse(inner, limit, "ozon", "Ozon", "https://www.ozon.ru")
            },
            headers = mapOf("Accept" to "*/*"),
            warm = "https://www.ozon.ru/",
            timeout = 7000,
        ),
    ),
)

/**
 * Avito не отдаёт открытый поиск для приложений и режет запросы без браузера,
 * поэтому здесь только честная ссылка на поиск в браузере: результат всё равно
 * открывается мгновенно, парсить их HTML бессмысленно и хрупко. Поэтому сеть
 * не трогаем вовсе — сразу отдаём понятную ошибку.
 */
object AvitoSource : PartsSource {
    override val id = "avito"
    override val title = "Avito"
    override val unofficial = true

    override fun searchUrl(query: String) =
        "https://www.avito.ru/search?q=${Http.enc(query)}"

    override suspend fun search(query: String, limit: Int): List<PartOffer> =
        throw UnsupportedOperationException("Avito отдаёт поиск только браузеру")
}
