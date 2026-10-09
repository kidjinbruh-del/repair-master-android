package com.repairmaster.app.data.parts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject

/** Локальная база деталей из assets/db/parts.json: работает без сети. */
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
    )

    @Volatile
    private var entries: List<Entry>? = null

    fun all(): List<Entry> = entries ?: synchronized(this) {
        entries ?: load().also { entries = it }
    }

    private fun load(): List<Entry> {
        val raw = appContext.assets.open("db/parts.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(raw).getJSONArray("parts")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                id = o.optString("id"),
                name = o.optString("name"),
                marking = o.optString("marking"),
                type = o.optString("type"),
                packageName = o.optString("package"),
                params = rows(o.optJSONArray("params")),
                equivalents = list(o.optJSONArray("equivalents")),
                note = o.optString("note"),
                searchQuery = o.optString("searchQuery"),
                image = o.optString("image").takeIf { it.isNotBlank() },
            )
        }
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

/** Настройки модуля: включатели источников и режим загрузки картинок. */
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
}

/** Поиск деталей: умный запрос по базе + живой поиск в источниках + ссылки. */
class PartsRepository(
    context: Context,
    private val db: PartsDb = PartsDb(context),
    private val settings: PartsSettings = PartsSettings(context),
) {

    private val liveSources: List<PartsSource> =
        listOf(YandexMarketSource(), WildberriesSource(), OzonSource(), AvitoSource())

    fun allEntries(): List<PartsDb.Entry> = db.all()

    fun sources(): List<PartsSource> = liveSources

    fun isUnofficialEnabled(): Boolean = settings.unofficialEnabled

    fun isImagesEnabled(): Boolean = settings.imagesEnabled

    fun imageMode(): Int = settings.imageMode

    /** Угадывает по базе, что ищет пользователь, и уточняет запрос для магазинов. */
    fun match(rawQuery: String): Pair<PartsDb.Entry?, String> {
        val q = normalize(rawQuery)
        if (q.isBlank()) return null to rawQuery.trim()
        val entries = db.all()

        // 1) точное совпадение маркировки или её части
        val exact = entries.firstOrNull { e ->
            val m = normalize(e.marking)
            m.contains(q) || q.contains(m) && m.length >= 3
        }
        if (exact != null) return exact to exact.searchQuery

        // 2) совпадение по токенам (маркировка, название, тип, корпус)
        val tokens = q.split(' ').filter { it.length >= 2 }
        val scored = entries.map { e ->
            var score = 0
            val hay = normalize("${e.marking} ${e.name} ${e.type} ${e.packageName} ${e.equivalents.joinToString(" ")}")
            tokens.forEach { t -> if (hay.contains(t)) score++ }
            score to e
        }.filter { it.first > 0 }.sortedByDescending { it.first }

        val best = scored.firstOrNull()
        return if (best != null && best.first >= 1) best.second to best.second.searchQuery
        else null to rawQuery.trim()
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9.+-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    suspend fun search(rawQuery: String, perSourceLimit: Int = 20): PartsResult {
        val t0 = System.currentTimeMillis()
        val (match, smart) = match(rawQuery)
        val offers = ArrayList<PartOffer>()
        val statuses = ArrayList<SourceStatus>()

        if (settings.unofficialEnabled) {
            coroutineScope {
                val jobs = liveSources.map { src ->
                    async(Dispatchers.IO) { src to runCatching { src.search(smart, perSourceLimit) } }
                }
                jobs.awaitAll().forEach { (src, res) ->
                    res.onSuccess { list ->
                        offers += list
                        statuses += SourceStatus(
                            src.id, src.title,
                            if (list.isEmpty()) "empty" else "ok",
                            if (list.isEmpty()) "ничего не найдено" else "",
                            list.size,
                        )
                    }
                    res.onFailure { err ->
                        statuses += SourceStatus(
                            src.id, src.title, "failed",
                            friendlyError(err), 0,
                        )
                    }
                }
            }
        } else {
            liveSources.forEach {
                statuses += SourceStatus(it.id, it.title, "off", "выключен в настройках", 0)
            }
        }

        return PartsResult(
            query = rawQuery.trim(),
            smartQuery = smart,
            dbMatch = match?.let {
                PartMatch(
                    it.id, it.name, it.marking, it.type, it.packageName, it.params,
                    it.equivalents, it.note, it.searchQuery, it.image,
                )
            },
            offers = offers,
            statuses = statuses,
            links = ShopLinks.all(smart),
            ms = System.currentTimeMillis() - t0,
        )
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            e is UnsupportedOperationException -> msg
            msg.contains("HTTP 403") -> "площадка заблокировала запрос (403)"
            msg.contains("HTTP 429") -> "слишком много запросов (429)"
            msg.contains("HTTP 401") -> "нужен другой доступ (401)"
            msg.contains("timeout", true) -> "нет ответа (таймаут)"
            msg.contains("Unable to resolve") || msg.contains("UnknownHost") -> "нет интернета"
            msg.isBlank() -> "ошибка источника"
            else -> msg.take(70)
        }
    }
}