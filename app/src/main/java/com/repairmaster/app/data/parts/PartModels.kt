package com.repairmaster.app.data.parts

/** Товар, найденный в магазине: название, цена, ссылка, картинка. */
data class PartOffer(
    val sourceId: String,
    val shop: String,
    val title: String,
    val priceRub: Double? = null,
    val oldPriceRub: Double? = null,
    val url: String,
    val imageUrl: String? = null,
    val rating: Double? = null,
    val reviews: Int? = null,
    val inStock: Boolean = true,
)

/** Статус источника после попытки поиска — показываем честно, что получилось. */
data class SourceStatus(
    val sourceId: String,
    val title: String,
    val state: String,      // ok | empty | failed | off
    val detail: String = "",
    val found: Int = 0,
)

/** Площадка для быстрой ссылки: работает всегда, ключи и парсинг не нужны. */
data class ShopLink(
    val id: String,
    val title: String,
    val kind: String,       // marketplace | electronics | images
    val url: String,
)

/** Результат поиска целиком: живые офферы + статусы + ссылки. */
data class PartsResult(
    val query: String,
    val smartQuery: String,
    val dbMatch: PartMatch?,
    val offers: List<PartOffer>,
    val statuses: List<SourceStatus>,
    val links: List<ShopLink>,
    val ms: Long,
)

/** Совпадение по встроенной базе деталей: что это и какие параметры. */
data class PartMatch(
    val id: String,
    val name: String,
    val marking: String,
    val type: String,
    val packageName: String,
    val params: List<List<String>>,
    val equivalents: List<String>,
    val note: String,
    val searchQuery: String,
    val image: String?,
)

/** Источник живого поиска. */
interface PartsSource {
    val id: String
    val title: String

    /** Неофициальный парсинг: работает не всегда и может быть отключён в настройках. */
    val unofficial: Boolean

    /** Всегда доступная ссылка на поиск по запросу. */
    fun searchUrl(query: String): String

    /** Живой поиск. Бросает исключение, если источник недоступен. */
    suspend fun search(query: String, limit: Int): List<PartOffer>
}