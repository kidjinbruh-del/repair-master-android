package com.repairmaster.app.data.parts

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Парсеры ответов площадок. Фикстуры — реальные куски живых страниц, так что
 * тест ломается сразу, как только площадка поменяет разметку.
 */
class PartsParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream(name)) { "нет фикстуры $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `яндекс маркет schema org`() {
        val offers = JsonLdParser.parse(fixture("/ym_ldjson.html"), 20, "ym", "Яндекс Маркет")
        assertTrue("ожидали товары, получили ${offers.size}", offers.isNotEmpty())
        assertEquals(6, offers.size)
        val first = offers.first()
        assertEquals("Микросхема L7805CV", first.title)
        assertEquals(147.0, first.priceRub!!, 0.01)
        assertEquals("https://market.yandex.ru/card/mikroskhema-l7805cv/4593735565", first.url)
        assertTrue(first.inStock)
        assertTrue(first.imageUrl!!.startsWith("https://avatars.mds.yandex.net"))
        assertEquals("Яндекс Маркет", first.shop)
    }

    @Test
    fun `в лимит не выдаём больше запрошенного`() {
        val offers = JsonLdParser.parse(fixture("/ym_ldjson.html"), 3, "ym", "Яндекс Маркет")
        assertEquals(3, offers.size)
    }

    @Test
    fun `chipdip строки товаров`() {
        val offers = ChipDipParser.parse(
            fixture("/chipdip_rows.html"),
            20,
            "https://www.chipdip.ru",
        )
        assertEquals(3, offers.size)
        val first = offers.first()
        assertTrue(first.title.startsWith("7805 TO-220"))
        assertEquals("ChipDip", first.shop)
        assertEquals(44.0, first.priceRub!!, 0.01)
        assertTrue(first.inStock)
        assertTrue(
            first.url.startsWith("https://www.chipdip.ru/product/7805-to-220"),
        )
        // Товар под заказ — наличие должно быть false.
        assertTrue(offers.any { !it.inStock })
    }

    @Test
    fun `цена с неразрывным пробелом и сущностью 160`() {
        // ChipDip разделяет разряды как &#160; — цифры сущности не должны
        // приклеиваться к цене: 4 810 ₽, а не 4160810 ₽.
        val html = """
            <div class="product_row">
            <a class="link_menu clear" href="/product/test-123">
            <div class="name"><b>7805</b> TO-220</div>
            <div><span class="item__avail item__avail_available nw">2 шт.</span></div>
            </a>
            <div class="product_row_controls">
            <div class="price"><span id="price_1">4&#160;810</span><span class="rub"> ₽</span></div>
            </div>
            </div>
            <div class="product_row">
            <a class="link_menu clear" href="/product/test-456">
            <div class="name">Дисплей iPhone 12</div>
            <div><span class="item__avail item__avail_delivery">под заказ</span></div>
            </a>
            <div class="product_row_controls">
            <div class="price"><span id="price_2">12&#160;960</span><span class="rub"> ₽</span></div>
            </div>
            </div>
        """.trimIndent()
        val offers = ChipDipParser.parse(html, 10, "https://www.chipdip.ru")
        assertEquals(2, offers.size)
        assertEquals(4810.0, offers[0].priceRub!!, 0.01)
        assertEquals(12960.0, offers[1].priceRub!!, 0.01)
        assertTrue(offers[0].inStock)
        assertTrue(!offers[1].inStock)
    }

    @Test
    fun `числовые html-сущности раскрываются`() {
        assertEquals("4\u00a0810", decodeNumericEntities("4&#160;810"))
        assertEquals("A'B", decodeNumericEntities("A&#39;B"))
        assertEquals("<x>", decodeNumericEntities("&#x3C;x&#x3E;"))
        // Битые и некорректные значения не ломают строку.
        assertEquals("&#999999999;", decodeNumericEntities("&#999999999;"))
        assertEquals("&#;", decodeNumericEntities("&#;"))
    }

    @Test
    fun `товар без цены не получает нулевую цену`() {
        val html = """
            <div class="product_row">
            <a class="link_menu clear" href="/product/12345">
            <div class="name">Деталь под заказ</div>
            <span class="item__avail item__avail_order">под заказ</span>
            </a>
            <div class="product_row_controls">
            <div class="price">по запросу</div>
            </div>
            </div>
        """.trimIndent()
        val offers = ChipDipParser.parse(html, 10, "https://www.chipdip.ru")
        assertEquals(1, offers.size)
        assertEquals(null, offers.first().priceRub)
        assertTrue(!offers.first().inStock)
    }

    @Test
    fun `цена в копейсках wildberries`() {
        val json = JSONObject(
            """
            {"products":[{"id":123,"name":"Диод 1N4007","priceU":450,"rating":4.5,
            "feedbacks":{"total":12},"sizes":[{"price":450}]}]}
            """.trimIndent(),
        )
        val offers = JsonApiParser.parse(json, 10, "wb", "Wildberries") { p ->
            p.optLong("id", 0L).takeIf { it > 0 }
                ?.let { "https://www.wildberries.ru/catalog/$it/detail.aspx" }
        }
        assertEquals(1, offers.size)
        assertEquals(4.5, offers.first().priceRub!!, 0.01)
        assertEquals(4.5, offers.first().rating!!, 0.01)
        assertEquals(12, offers.first().reviews)
        assertEquals("https://www.wildberries.ru/catalog/123/detail.aspx", offers.first().url)
    }

    @Test
    fun `ozon composer widgetStates`() {
        val inner = JSONObject()
            .put("items", JSONArray().put(JSONObject().put("mainState", JSONObject()
                .put("title", "Микросхема 7805")
                .put("price", JSONObject().put("price", "199.00"))
                .put("link", "/product/7805-123/"))))
        val widgets = JSONObject().put(
            "searchResultsV2-123456789",
            inner.toString(),
        )
        val offers = JsonApiParser.parse(inner, 10, "ozon", "Ozon", "https://www.ozon.ru")
        assertEquals(1, offers.size)
        assertEquals("Микросхема 7805", offers.first().title)
        assertEquals(199.0, offers.first().priceRub!!, 0.01)
        assertTrue(offers.first().url.startsWith("https://www.ozon.ru/product/"))
        // widgetStates отдают вложенной JSON-строкой — проверяем, что она читается.
        val key = widgets.keys().next()
        val unpacked = JSONObject(widgets.optString(key))
        assertEquals(1, unpacked.optJSONArray("items")!!.length())
    }

    @Test
    fun `пустой ответ не роняет парсер`() {
        assertTrue(JsonLdParser.parse("<html>нет данных</html>", 10, "ym", "test").isEmpty())
        assertTrue(ChipDipParser.parse("<html></html>", 10, "https://x.ru").isEmpty())
        assertTrue(
            JsonApiParser.parse(
                JSONObject("{\"unexpected\":{\"deep\":{\"list\":[]}}}"),
                10, "t", "t",
            ).isEmpty(),
        )
    }

    @Test
    fun `массив товаров ищется на глубине`() {
        val json = JSONObject(
            """
            {"data":{"state":{"search":{"products":[
              {"name":"Резистор 10к","price":5,"url":"https://shop.ru/1"},
              {"name":"Резистор 1к","price":3,"url":"https://shop.ru/2"}
            ]}}}}
            """.trimIndent(),
        )
        val offers = JsonApiParser.parse(json, 10, "deep", "Магазин")
        assertEquals(2, offers.size)
        assertEquals("Резистор 10к", offers.first().title)
        assertEquals(5.0, offers.first().priceRub!!, 0.01)
    }

    @Test
    fun `ссылка на товар собирается по fallback`() {
        val json = JSONObject(
            """{"products":[{"id":45511233,"name":"Диод 1N4007","priceU":450}]}""",
        )
        val offers = JsonApiParser.parse(json, 10, "wb", "Wildberries") { p ->
            p.optLong("id", 0L).takeIf { it > 0 }
                ?.let { "https://www.wildberries.ru/catalog/$it/detail.aspx" }
        }
        assertTrue(offers.isNotEmpty())
        assertEquals("https://www.wildberries.ru/catalog/45511233/detail.aspx", offers.first().url)
    }
}
