package com.repairmaster.app.data.parts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разбор запроса мастера — ядро подбора деталей. Если он сломается, поиск
 * уйдёт в магазины с мусором, поэтому проверяем на типовых запросах ремонта.
 */
class QueryAnalyzerTest {

    private val analyzer = QueryAnalyzer()

    private fun analyze(q: String) = analyzer.analyze(q)

    @Test
    fun `чистая маркировка`() {
        val q = analyze("1N4007")
        assertEquals("1N4007", q.marking)
        assertNull(q.model)
        assertTrue(q.variants.first().text.equals("1N4007", ignoreCase = true))
    }

    @Test
    fun `цифровая маркировка микросхемы`() {
        assertEquals("7805", analyze("7805").marking)
        assertEquals("7812", analyze("микросхема 7812").marking)
    }

    @Test
    fun `маркировка не путается с короткими числами`() {
        assertNull(analyze("iPhone 12").marking)
        assertNull(analyze("Android 13").marking)
    }

    @Test
    fun `маркировка с описанием`() {
        val q = analyze("микросхема LM2596")
        assertEquals("LM2596", q.marking)
    }

    @Test
    fun `модель и деталь из справочника`() {
        val q = analyze("Philips HR1858 ремень")
        assertEquals("Philips HR1858", q.model)
        assertEquals("Ремень", q.partTerm)
        assertTrue(q.variants.any { it.text == "Philips HR1858 Ремень" })
    }

    @Test
    fun `модель с именным кодом без цифр`() {
        val q = analyze("Keenetic Giga блок питания")
        assertEquals("Keenetic Giga", q.model)
        assertEquals("Блок питания", q.partTerm)
    }

    @Test
    fun `телефон и тачскрин`() {
        val q = analyze("iPhone 12 дисплей")
        assertEquals("iPhone 12", q.model)
        assertEquals("Дисплей", q.partTerm)
    }

    @Test
    fun `тачскрин точнее стекла`() {
        assertEquals("Дисплей (тачскрин)", analyze("Xiaomi Redmi Note 12 тачскрин").partTerm)
    }

    @Test
    fun `модель и бренд раздельно`() {
        val q = analyze("Bosch MUC2 венчик")
        assertEquals("Bosch MUC2", q.model)
        assertEquals("Bosch", q.brand)
    }

    @Test
    fun `подшипник и его номер`() {
        val q = analyze("подшипник 6205")
        assertEquals("Подшипник", q.partTerm)
        assertEquals("6205", q.marking)
    }

    @Test
    fun `параметры конденсатора`() {
        val q = analyze("конденсатор 470 мкФ 25В")
        assertEquals("Конденсатор", q.partTerm)
        assertTrue(q.params.isNotEmpty())
    }

    @Test
    fun `варианты уникальны и отсортированы по весу`() {
        val q = analyze("Jura E8 уплотнитель")
        val texts = q.variants.map { it.text }
        assertEquals(texts.size, texts.distinctBy { it.lowercase() }.size)
        val weights = q.variants.map { it.weight }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `пустой запрос не ломает разбор`() {
        assertTrue(analyze("   ").variants.isEmpty())
    }

    @Test
    fun `haoHAO shit类型的 запрос не роняет разбор`() {
        assertTrue(analyze("ча世界").variants.isNotEmpty())
    }

    @Test
    fun `модель без детали даёт запрос только по модели`() {
        val q = analyze("Samsung Galaxy A50")
        assertEquals("Samsung Galaxy A50", q.model)
        assertTrue(q.variants.any { it.text == "Samsung Galaxy A50" })
    }
}
