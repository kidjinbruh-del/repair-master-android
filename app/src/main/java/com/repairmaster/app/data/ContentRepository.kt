package com.repairmaster.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Загружает контент из assets/content/catalog.json (полностью офлайн). */
class ContentRepository(context: Context) {

    private val appContext = context.applicationContext

    data class Catalog(val categories: List<Category>, val problems: List<Problem>)

    @Volatile
    private var cache: Catalog? = null

    fun load(): Catalog = cache ?: synchronized(this) {
        cache ?: parse().also { cache = it }
    }

    private fun parse(): Catalog {
        val categories = mutableListOf<Category>()
        val files = appContext.assets.list("content")?.sorted()
            ?.filter { it.endsWith(".json") } ?: emptyList()
        files.forEach { name ->
            val raw = appContext.assets.open("content/$name")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            categories += root.getJSONArray("categories").mapObjects { cat ->
                Category(
                    id = cat.getString("id"),
                    title = cat.getString("title"),
                    icon = cat.optString("icon", "chip"),
                    summary = cat.optString("summary", ""),
                    group = cat.optString("group", "reference"),
                    items = cat.getJSONArray("items").mapObjects { item -> parseItem(item) },
                )
            }
        }
        val problems = categories.flatMap { c ->
            c.items.flatMap { item -> item.problems + item.models.flatMap { it.problems } }
        }
        return Catalog(categories, problems)
    }

    private fun parseItem(item: JSONObject): GuideItem {
        val id = item.getString("id")
        val blocks = item.optJSONArray("blocks")?.mapObjects { parseBlock(it) } ?: emptyList()
        val problems = item.optJSONArray("problems")?.mapObjects { parseProblem(it, id) } ?: emptyList()
        val models = item.optJSONArray("models")?.mapObjects { parseModel(it, id) } ?: emptyList()
        return GuideItem(
            id = id,
            title = item.getString("title"),
            summary = item.optString("summary", ""),
            images = item.optJSONArray("images").toStringList(),
            blocks = blocks,
            warnings = item.optJSONArray("warnings").mapObjects {
                Warning(it.optString("level", "caution"), it.getString("text"))
            },
            problems = problems,
            models = models,
        )
    }

    private fun parseModel(o: JSONObject, parentId: String): DeviceModel = DeviceModel(
        name = o.getString("name"),
        brand = o.optString("brand", ""),
        years = o.optString("years", ""),
        note = o.optString("note", ""),
        specs = o.optJSONArray("specs").mapArrays { it.toStringList() },
        problems = o.optJSONArray("problems")?.mapObjects { parseProblem(it, parentId) }
            ?: emptyList(),
    )

    private fun parseBlock(o: JSONObject): ContentBlock {
        val type = o.optString("type", "steps")
        val heading = o.optString("heading", "")
        val icon = o.optString("icon", "steps")
        return if (type == "table") {
            ContentBlock.Table(
                heading = heading,
                icon = icon,
                headers = o.optJSONArray("headers").toStringList(),
                rows = o.optJSONArray("rows").mapArrays { it.toStringList() },
            )
        } else {
            ContentBlock.Steps(heading, icon, o.optJSONArray("items").toStringList())
        }
    }

    private fun parseProblem(o: JSONObject, parentId: String): Problem = Problem(
        id = o.optString("id", parentId),
        title = o.getString("title"),
        severity = o.optString("severity", "medium"),
        device = o.optString("device", "Универсально"),
        symptoms = o.optJSONArray("symptoms").toStringList(),
        causes = o.optJSONArray("causes").mapObjects {
            Cause(it.optString("text", ""), it.optString("check", ""))
        },
        diagnostics = o.optJSONArray("diagnostics").mapObjects { parseAction(it) },
        fix = o.optJSONArray("fix").mapObjects { parseAction(it) },
        tools = o.optJSONArray("tools").toStringList(),
        warnings = o.optJSONArray("warnings").toStringList(),
        images = o.optJSONArray("images").toStringList(),
        timeEstimate = o.optString("time", "30–60 мин"),
    )

    private fun parseAction(o: JSONObject) = Action(
        title = o.optString("title", ""),
        detail = o.optString("detail", ""),
    )
}

private fun <T> JSONArray.mapObjects(fn: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(fn) }

private fun <T> JSONArray?.mapArrays(fn: (JSONArray) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONArray(i)?.let(fn) }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { i -> optString(i, "") }.filter { it.isNotBlank() }
}

private fun JSONObject.toStringList(): List<String> {
    val out = mutableListOf<String>()
    keys().forEach { k -> optString(k, "").takeIf { it.isNotBlank() }?.let(out::add) }
    return out
}