package com.repairmaster.app.data.parts

import com.repairmaster.app.data.parts.PartOffer
import com.repairmaster.app.data.parts.PartsSource
import org.json.JSONObject

/**
 * Неофициальный источник: внутренний эндпоинт поиска Wildberries.
 * Формат ответа меняется, при неудаче бросаем исключение — приложение покажет
 * честный статус и предложит открыть поиск в браузере.
 */
class WildberriesSource : PartsSource {
    override val id = "wb"
    override val title = "Wildberries"
    override val unofficial = true

    override fun searchUrl(query: String) =
        "https://www.wildberries.ru/catalog/0/search.aspx?search=${Http.enc(query)}"

    override suspend fun search(query: String, limit: Int): List<PartOffer> {
        val cookies = Http.warmCookies("https://www.wildberries.ru/")
        val url = "https://search.wb.ru/exactmatch/ru/common/v7/search" +
            "?appType=1&curr=rub&dest=-1257786&query=${Http.enc(query)}" +
            "&resultset=catalog&limit=$limit&sort=popular&spp=30&suppressSpellcheck=false"
        val body = Http.get(
            url,
            mapOf(
                "Accept" to "*/*",
                "Origin" to "https://www.wildberries.ru",
                "Referer" to "https://www.wildberries.ru/",
                "Cookie" to cookies,
            ),
            timeout = 7000,
        )
        val root = JSONObject(body)
        val products = root.optJSONArray("products") ?: return emptyList()
        val out = ArrayList<PartOffer>(products.length())
        for (i in 0 until products.length()) {
            val p = products.optJSONObject(i) ?: continue
            val productId = p.optLong("id", 0L)
            if (productId == 0L) continue
            val title = p.optString("name")
            if (title.isBlank()) continue
            val price = wbPrice(p)
            val img = wbImage(productId)
            val rating = p.optDoubleOrNull("rating")
            val reviews = p.optJSONObject("feedbacks")?.optInt("total", 0)
            out += PartOffer(
                sourceId = id,
                shop = title,
                title = title,
                priceRub = price,
                url = "https://www.wildberries.ru/catalog/$productId/detail.aspx",
                imageUrl = img,
                rating = rating,
                reviews = reviews?.takeIf { it > 0 },
            )
        }
        return out
    }

    /** Цена в копейках лежит в sizes — формат ответа менялся, проверяем оба вида. */
    private fun wbPrice(p: JSONObject): Double? {
        val kop = when (val sizes = p.opt("sizes")) {
            is org.json.JSONArray -> sizes.optJSONObject(0)?.optInt("price", 0) ?: 0
            is JSONObject -> sizes.optJSONObject("0")?.optInt("price", 0) ?: 0
            else -> 0
        }
        val v = if (kop > 0) kop else p.optInt("priceU", 0)
        return if (v > 0) v / 100.0 else null
    }

    /** Картинка WB собирается по их же правилам нумерации. */
    private fun wbImage(id: Long): String {
        val vol = id / 100000
        val part = id / 1000
        return "https://basket-03.wbbasket.ru/vol$vol/part$part/$id/images/big/1.webp"
    }
}

/**
 * Неофициальный источник: внутренний composer-API Ozon.
 * Ответ приходит вложенным JSON-строкой в widgetStates — аккуратно распаковываем.
 */
class OzonSource : PartsSource {
    override val id = "ozon"
    override val title = "Ozon"
    override val unofficial = true

    override fun searchUrl(query: String) =
        "https://www.ozon.ru/search/?text=${Http.enc(query)}"

    override suspend fun search(query: String, limit: Int): List<PartOffer> {
        val cookies = Http.warmCookies("https://www.ozon.ru/")
        val body = Http.get(
            "https://www.ozon.ru/api/composer-api.bx/page/json/v2" +
                "?url=/search/?text=${Http.enc(query)}&from_global=true&sorting=rating",
            mapOf(
                "Accept" to "*/*",
                "x-o3-app-name" to "dapi_client",
                "x-o3-app-version" to "release_17000000",
                "Origin" to "https://www.ozon.ru",
                "Referer" to "https://www.ozon.ru/",
                "Cookie" to cookies,
            ),
            timeout = 7000,
        )
        val root = JSONObject(body)
        val widgetStates = root.optJSONObject("widgetStates")
            ?: error("Ozon: нет widgetStates")
        val searchKey = widgetStates.keys().asSequence()
            .firstOrNull { it.startsWith("searchResultsV2") }
            ?: error("Ozon: не найден блок поиска")
        val inner = JSONObject(widgetStates.optString(searchKey))
        val items = inner.optJSONArray("items") ?: error("Ozon: нет items")

        val out = ArrayList<PartOffer>(minOf(items.length(), limit))
        for (i in 0 until items.length()) {
            if (out.size >= limit) break
            val main = items.optJSONObject(i)?.optJSONObject("mainState") ?: continue
            val title = main.optString("title")
            if (title.isBlank()) continue
            val priceObj = main.optJSONObject("price")
            val price = priceObj?.optString("price")?.toDoubleOrNull()
            val oldPrice = priceObj?.optString("originalPrice")?.toDoubleOrNull()
            val link = main.optString("link")
            val image = main.optJSONObject("image")?.optJSONArray("items")
                ?.optJSONObject(0)?.optString("link")
                ?: main.optJSONArray("images")?.optJSONObject(0)?.optString("link")
            val rating = main.optJSONObject("rating")
                ?.optDoubleOrNull("average")
            out += PartOffer(
                sourceId = "$id-$i",
                shop = title,
                title = title,
                priceRub = price,
                oldPriceRub = oldPrice?.takeIf { it > (price ?: 0.0) },
                url = "https://www.ozon.ru$link",
                imageUrl = image,
                rating = rating,
            )
        }
        return out
    }
}

/**
 * Avito не отдаёт открытый поиск для приложений и режет запросы без браузера,
 * поэтому здесь только честная ссылка на поиск в браузере: результат всё равно
 * открывается мгновенно, парсить их HTML бессмысленно и хрупко.
 */
class AvitoSource : PartsSource {
    override val id = "avito"
    override val title = "Avito"
    override val unofficial = true

    override fun searchUrl(query: String) =
        "https://www.avito.ru/sankt-peterburg/dostroy/remont_i_stroitelstvo/elektronika" +
            "?q=${Http.enc(query)}"

    override suspend fun search(query: String, limit: Int): List<PartOffer> =
        throw UnsupportedOperationException("Avito отдаёт поиск только браузеру")
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && it != 0.0 }

/**
 * Яндекс Маркет: страница поиска содержит структурированные данные Schema.org
 * (ItemList) — название, цена, фото, рейтинг и ссылка. Это самый устойчивый
 * канал из крупных площадок: не ломается при редизайне вёрстки.
 */
class YandexMarketSource : PartsSource {
    override val id = "ym"
    override val title = "Яндекс Маркет"
    override val unofficial = false

    override fun searchUrl(query: String) =
        "https://market.yandex.ru/search/?text=${Http.enc(query)}"

    private val ldRegex = Regex(
        "(?s)<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
        RegexOption.IGNORE_CASE,
    )

    override suspend fun search(query: String, limit: Int): List<PartOffer> {
        val html = Http.get(
            searchUrl(query),
            mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Sec-Fetch-Mode" to "navigate",
                "Sec-Fetch-Site" to "none",
                "Upgrade-Insecure-Requests" to "1",
            ),
            timeout = 15000,
        )
        val out = ArrayList<PartOffer>()
        for (m in ldRegex.findAll(html)) {
            val json = cleanJson(m.groupValues[1]) ?: continue
            val root = runCatching { JSONObject(json) }.getOrNull() ?: continue
            val elements = root.optJSONArray("itemListElement") ?: continue
            for (i in 0 until elements.length()) {
                if (out.size >= limit) break
                val item = elements.optJSONObject(i)?.optJSONObject("item") ?: continue
                val title = item.optString("name")
                if (title.isBlank()) continue
                val url = item.optString("url").ifBlank { item.optString("@id") }
                if (url.isBlank()) continue
                val offers = item.optJSONObject("offers")
                val price = offers?.optDoubleOrNull("price")
                val rating = item.optJSONObject("aggregateRating")
                val inStock = offers?.optString("availability")?.contains("InStock") ?: true
                out += PartOffer(
                    sourceId = "$id-$i-${item.optString("sku")}",
                    shop = "Яндекс Маркет",
                    title = title,
                    priceRub = price,
                    url = url,
                    imageUrl = item.optString("image").takeIf { it.isNotBlank() },
                    rating = rating?.optDoubleOrNull("ratingValue"),
                    reviews = rating?.optInt("ratingCount", 0)?.takeIf { it > 0 },
                    inStock = inStock,
                )
            }
        }
        if (out.isEmpty()) error("Яндекс Маркет: в ответе нет списка товаров")
        return out
    }

    /** Убирает HTML-экранирование и обрезку вокруг JSON. */
    private fun cleanJson(raw: String): String? {
        var s = raw.trim()
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        s = s.substring(start, end + 1)
        return s.ifBlank { null }
    }
}