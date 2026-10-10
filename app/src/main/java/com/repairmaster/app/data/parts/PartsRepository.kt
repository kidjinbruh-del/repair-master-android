package com.repairmaster.app.data.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** Локальная база деталей из assets/db (json-файлы): работает без сети. */
class PartsDb(context: Context) {

    private val appContext = context.applicationContext

    data class Entry(
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
        val models: List<String> = emptyList(),
    )

    @Volatile
    private var entries: List<Entry>? = null

    fun all(): List<Entry> = entries ?: synchronized(this) {
        entries ?: load().also { entries = it }
    }

    private fun load(): List<Entry> {
        val files = try {
            appContext.assets.list("db")?.sorted()?.filter { it.endsWith(".json") }
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<Entry>()
        files.forEach { name ->
            // Одна битая запись не должна уносить всю базу: читаем максимально терпимо.
            val raw = try {
                appContext.assets.open("db/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                return@forEach
            }
            val arr = try {
                JSONObject(raw).optJSONArray("parts")
            } catch (e: Exception) {
                null
            } ?: return@forEach
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val nameOf = o.optString("name").trim()
                if (nameOf.isBlank()) continue
                out += Entry(
                    id = o.optString("id").ifBlank { "$name-$i" },
                    name = nameOf,
                    marking = o.optString("marking").ifBlank { nameOf },
                    type = o.optString("type"),
                    packageName = o.optString("package"),
                    params = rows(o.optJSONArray("params")),
                    equivalents = list(o.optJSONArray("equivalents")),
                    note = o.optString("note"),
                    searchQuery = o.optString("searchQuery")
                        .ifBlank { o.optString("marking").ifBlank { nameOf } },
                    image = o.optString("image").takeIf { it.isNotBlank() },
                    models = list(o.optJSONArray("models")),
                )
            }
        }
        return out
    }

    /** Свой список моделей: `{"models": ["Philips HR1858"]}` — необязательно. */
    fun deviceModels(): List<String> = try {
        val raw = appContext.assets.open("db/device_models.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        list(JSONObject(raw).optJSONArray("models"))
    } catch (e: Exception) {
        emptyList()
    }

    private fun rows(a: JSONArray?): List<List<String>> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val r = a.optJSONObject(i) ?: return@mapNotNull null
            val cells = (0 until r.length()).map { c -> r.optString(c.toString(), "") }
            cells.takeIf { it.isNotEmpty() && it[0].isNotBlank() }
        }
    }

    private fun list(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it, "") }.filter { it.isNotBlank() }
    }
}

/**
 * Настройки модуля: включатели источников, режим картинок и «здоровье» площадок.
 * Здоровье нужно, чтобы после серии блокировок не тратить время и трафик на
 * перебор адресов, которые только что не ответили.
 */
class PartsSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("parts", Context.MODE_PRIVATE)

    var unofficialEnabled: Boolean
        get() = prefs.getBoolean("unofficial", true)
        set(v) = prefs.edit().putBoolean("unofficial", v).apply()

    var imagesEnabled: Boolean
        get() = prefs.getInt("images", 1) != 0
        set(v) = prefs.edit().putInt("images", if (v) 1 else 0).apply()

    /** 0 — только Wi-Fi, 1 — всегда, 2 — не грузить. */
    var imageMode: Int
        get() = prefs.getInt("imageMode", 0)
        set(v) = prefs.edit().putInt("imageMode", v).apply()

    /** Сколько подряд неудач терпим, прежде чем временно отключить источник. */
    val failLimit: Int = 3

    /** Пауза после серии неудач, мс. */
    val pauseMs: Long = 20 * 60 * 1000

    fun failCount(id: String): Int = prefs.getInt("fail_$id", 0)

    fun lastFail(id: String): Long = prefs.getLong("failAt_$id", 0)

    fun markResult(id: String, ok: Boolean) {
        val fails = if (ok) 0 else failCount(id) + 1
        prefs.edit()
            .putInt("fail_$id", fails)
            .putLong("failAt_$id", if (ok) 0 else System.currentTimeMillis())
            .apply()
    }

    /** Источник недавно упорно отвечал блокировкой — пропускаем и честно пишем почему. */
    fun isPaused(id: String): Boolean =
        failCount(id) >= failLimit && System.currentTimeMillis() - lastFail(id) < pauseMs

    fun resetHealth() {
        prefs.edit().apply {
            prefs.all.keys
                .filter { it.startsWith("fail_") || it.startsWith("failAt_") }
                .forEach { remove(it) }
        }.apply()
    }
}

/** Итог разбора запроса: что и в каком порядке уйдёт в магазины. */
data class QueryPlan(
    val rawQuery: String,
    val smartQuery: String,
    val variants: List<QueryVariant>,
    val model: String?,
    val partTerm: String?,
    val marking: String?,
    /** Совпадение по встроенной базе, если оно есть. */
    val dbMatch: PartsDb.Entry? = null,
) {
    val queries: List<String>
        get() = variants.map { it.text }.distinct()
}

/** Поиск деталей: разбор запроса → база → живой поиск → ссылки на площадки. */
class PartsRepository(
    context: Context,
    private val db: PartsDb = PartsDb(context),
    private val settings: PartsSettings = PartsSettings(context),
) {

    private val liveSources: List<PartsSource> =
        listOf(
            YandexMarketSource(),
            ChipDipSource(),
            WildberriesSource(),
            OzonSource(),
            AvitoSource,
        )

    private val analyzer = QueryAnalyzer(
        (db.deviceModels().takeIf { it.isNotEmpty() } ?: QueryAnalyzer.DEVICE_MODELS),
    )

    fun allEntries(): List<PartsDb.Entry> = db.all()

    fun sources(): List<PartsSource> = liveSources

    fun isUnofficialEnabled(): Boolean = settings.unofficialEnabled

    fun isImagesEnabled(): Boolean = settings.imagesEnabled

    fun imageMode(): Int = settings.imageMode

    fun resetSourceHealth() = settings.resetHealth()

    // -----------------------------------------------------------------------
    // Разбор запроса
    // -----------------------------------------------------------------------

    /** Угадывает по базе, что ищет пользователь, и уточняет запрос для магазинов. */
    fun match(rawQuery: String): Pair<PartsDb.Entry?, String> {
        val q = normalize(rawQuery)
        if (q.isBlank()) return null to rawQuery.trim()
        val entries = db.all()
        if (entries.isEmpty()) return null to rawQuery.trim()

        // 1) Точное совпадение маркировки (целиком)
        val exact = entries.firstOrNull { e -> normalize(e.marking) == q }
        if (exact != null) return exact to exact.searchQuery

        // 2) Частичное совпадение маркировки с приоритетом по длине
        val partialMatches = entries.mapNotNull { e ->
            val m = normalize(e.marking)
            if (m.contains(q) || (q.contains(m) && m.length >= 3)) {
                // Чем длиннее маркировка, тем точнее совпадение
                m.length.toDouble() / q.length.toDouble() to e
            } else null
        }.sortedByDescending { it.first }

        if (partialMatches.isNotEmpty() && partialMatches.first().first > 0.8) {
            return partialMatches.first().second to partialMatches.first().second.searchQuery
        }

        // 3) Совпадение по токенам с весами
        val tokens = q.split(' ').filter { it.length >= 2 }
        if (tokens.isNotEmpty()) {
            val scored = entries.map { e ->
                var score = 0.0
                val hay = normalize(
                    "${e.marking} ${e.name} ${e.type} ${e.packageName} " +
                        e.equivalents.joinToString(" "),
                )
                tokens.forEach { t ->
                    when {
                        hay.contains(t) -> score += 1.0
                        normalize(e.marking).contains(t) -> score += 1.5 // маркировка важнее
                        normalize(e.name).contains(t) -> score += 1.0
                    }
                }
                // Бонус за совпадение типа детали
                if (tokens.any { normalize(e.type).contains(it) }) score += 0.5
                // Бонус за совпадение с моделью устройства
                if (e.models.any { m -> tokens.any { t -> normalize(m).contains(t) } }) {
                    score += 2.0
                }
                score to e
            }.filter { it.first > 0 }.sortedByDescending { it.first }

            val best = scored.firstOrNull()
            if (best != null && best.first >= 1.0) {
                return best.second to best.second.searchQuery
            }
        }

        // 4) Fallback: исходный запрос
        return null to rawQuery.trim()
    }

    /** Полный разбор: база + модель/деталь из запроса → список вариантов. */
    fun plan(rawQuery: String): QueryPlan {
        val q = rawQuery.trim()
        val (entry, smart) = match(q)
        val analyzed = analyzer.analyze(q)
        val variants = ArrayList<QueryVariant>()
        if (entry != null && entry.searchQuery.isNotBlank() && entry.searchQuery != q) {
            variants += QueryVariant(entry.searchQuery, 1.0, "маркировка из базы")
        }
        variants += analyzed.variants
        if (smart != q && variants.none { it.text.equals(smart, true) }) {
            variants += QueryVariant(smart, 0.95, "уточнено базой")
        }
        val ordered = variants
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { it.weight }
            .take(6)
        return QueryPlan(
            rawQuery = q,
            smartQuery = ordered.firstOrNull()?.text ?: q,
            variants = ordered,
            model = analyzed.model,
            partTerm = analyzed.partTerm,
            marking = analyzed.marking,
            dbMatch = entry,
        )
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9.+-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    // -----------------------------------------------------------------------
    // Живой поиск
    // -----------------------------------------------------------------------

    suspend fun search(rawQuery: String, perSourceLimit: Int = 20): PartsResult {
        val t0 = System.currentTimeMillis()
        val plan = plan(rawQuery)

        val result = runSources(plan, perSourceLimit)
        val offers = dedupe(result.first)
            .sortedWith(offerOrder(plan.queries.firstOrNull().orEmpty()))

        val match = plan.dbMatch
        return PartsResult(
            query = rawQuery.trim(),
            smartQuery = plan.smartQuery,
            dbMatch = match?.let {
                PartMatch(
                    it.id, it.name, it.marking, it.type, it.packageName, it.params,
                    it.equivalents, it.note, it.searchQuery, it.image, it.models,
                )
            },
            offers = offers,
            statuses = result.second,
            links = ShopLinks.all(plan.smartQuery),
            ms = System.currentTimeMillis() - t0,
            model = plan.model,
            partTerm = plan.partTerm,
            marking = plan.marking,
            suggestions = plan.variants.drop(1).take(4),
            queriesUsed = result.third,
        )
    }

    /**
     * Параллельный опрос источников. Если все промолчали — второй круг по
     * следующему варианту запроса: «Philips HR1858 ремень» может не найтись,
     * а «HR1858 ремень» или «ремень для Philips HR1858» — найтись.
     */
    private suspend fun runSources(
        plan: QueryPlan,
        limit: Int,
    ): Triple<List<PartOffer>, List<SourceStatus>, List<String>> = coroutineScope {
        val statuses = LinkedHashMap<String, SourceStatus>()
        val queries = LinkedHashSet<String>()
        var offers: List<PartOffer> = emptyList()
        val rounds = if (plan.queries.size > 1) 2 else 1

        for (round in 0 until rounds) {
            val query = plan.queries.getOrNull(round) ?: break
            queries += query
            val (statusList, foundOffers) = queryAllSources(query, limit)
            statusList.forEach { statuses[it.sourceId] = it }
            offers = foundOffers
            if (offers.isNotEmpty()) break
        }

        if (settings.unofficialEnabled.not()) {
            liveSources.filter { it.unofficial }.forEach {
                statuses[it.id] = SourceStatus(it.id, it.title, "off", "выключен в настройках", 0)
            }
        }
        Triple(offers, statuses.values.toList(), queries.toList())
    }

    /** Результат опроса одной площадки: статус для интерфейса и, если повезло, товары. */
    private data class Outcome(
        val status: SourceStatus,
        val offers: List<PartOffer>?,
    )

    private suspend fun queryAllSources(
        query: String,
        limit: Int,
    ): Pair<List<SourceStatus>, List<PartOffer>> = coroutineScope {
        val active = liveSources.filter { it.unofficial.not() || settings.unofficialEnabled }

        val jobs = active.map { src ->
            async(Dispatchers.IO) {
                if (settings.isPaused(src.id)) {
                    return@async Outcome(
                        SourceStatus(
                            src.id, src.title, "off",
                            "не отвечает, проверено недавно", 0,
                        ),
                        null,
                    )
                }
                runCatching {
                    val offers = searchWithRetry(src, query, limit)
                    if (offers.isEmpty()) {
                        throw EmptyResultException("${src.title}: ответил, но товаров нет")
                    }
                    offers
                }.fold(
                    onSuccess = { list ->
                        settings.markResult(src.id, ok = true)
                        Outcome(
                            SourceStatus(src.id, src.title, "ok", "", list.size),
                            list,
                        )
                    },
                    onFailure = { err ->
                        val empty = err is EmptyResultException
                        settings.markResult(src.id, ok = false)
                        Outcome(
                            SourceStatus(
                                src.id,
                                src.title,
                                if (empty) "empty" else "failed",
                                if (empty) "ничего не найдено" else friendlyError(err),
                                0,
                            ),
                            null,
                        )
                    },
                )
            }
        }

        val outcomes = jobs.awaitAll()
        outcomes.map { it.status } to outcomes.flatMap { it.offers.orEmpty() }
    }

    private class EmptyResultException(message: String) : Exception(message)

    /** Повтор только там, где он помогает: 403/429/таймаут. */
    private suspend fun searchWithRetry(
        src: PartsSource,
        query: String,
        limit: Int,
    ): List<PartOffer> {
        var lastError: Throwable? = null
        for (attempt in 0..1) {
            try {
                return src.search(query, limit)
            } catch (t: Throwable) {
                lastError = t
                if (t is EmptyResultException) throw t
                if (attempt == 0 && isRetryable(t)) delay(1200)
            }
        }
        throw lastError ?: error("${src.title}: нет ответа")
    }

    /** Дубли: один и тот же товар нередко приходит из двух источников. */
    private fun dedupe(offers: List<PartOffer>): List<PartOffer> {
        val seen = HashSet<String>()
        return offers.filter { o ->
            val key = normalize(o.title) + "|" + o.priceRub?.let { "$it" }.orEmpty()
            seen.add(key)
        }
    }

    /** Сначала то, что похоже на запрос, потом наличие, потом цена. */
    private fun offerOrder(query: String): Comparator<PartOffer> {
        val tokens = normalize(query).split(' ')
            .filter { it.length >= 3 }
            .toSet()
        fun relevance(o: PartOffer): Int {
            val t = normalize(o.title)
            return tokens.count { t.contains(it) } + if (o.inStock) 1 else 0
        }
        return compareByDescending<PartOffer> { relevance(it) }
            .thenByDescending { it.rating ?: 0.0 }
            .thenBy { it.priceRub ?: Double.MAX_VALUE }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is UnsupportedOperationException -> msg
            msg.contains("HTTP 403") -> "площадка заблокировала запрос (403)"
            msg.contains("HTTP 429") -> "слишком много запросов (429)"
            msg.contains("HTTP 401") -> "нужен другой доступ (401)"
            msg.contains("HTTP 498") || msg.contains("HTTP 5") -> "площадка недоступна"
            msg.contains("timeout", true) -> "нет ответа (таймаут)"
            msg.contains("Unable to resolve") || msg.contains("UnknownHost") -> "нет интернета"
            msg.isBlank() -> "ошибка источника"
            else -> msg.take(70)
        }
    }
}
