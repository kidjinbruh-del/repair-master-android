package com.repairmaster.app.data

/** Раздел верхнего уровня: пайка, инструмент, компоненты, провода, диагностика… */
data class Category(
    val id: String,
    val title: String,
    val icon: String,
    val summary: String,
    /** reference — общие знания, technique — конкретная техника */
    val group: String,
    val items: List<GuideItem>,
)

/** Статья: инструкция по теме + связанные проблемы. */
data class GuideItem(
    val id: String,
    val title: String,
    val summary: String,
    val images: List<String>,
    val blocks: List<ContentBlock>,
    val warnings: List<Warning>,
    val problems: List<Problem>,
    /** Конкретные модели/серии устройств со своими особенностями и поломками. */
    val models: List<DeviceModel> = emptyList(),
)

/** Модель или серия: бренд, годы, конструктивные особенности, свои поломки. */
data class DeviceModel(
    val name: String,
    val brand: String,
    val years: String,
    val note: String,
    val specs: List<List<String>>,
    val problems: List<Problem>,
)

/** Блок контента: нумерованный список или таблица. */
sealed class ContentBlock {
    data class Steps(
        val heading: String,
        val icon: String,
        val items: List<String>,
    ) : ContentBlock()

    data class Table(
        val heading: String,
        val icon: String,
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : ContentBlock()
}

/** Предупреждение по безопасности. */
data class Warning(
    val level: String, // danger | caution | info
    val text: String,
)

/** Инструкция к проблеме: симптом → причины → диагностика → ремонт. */
data class Problem(
    val id: String,
    val title: String,
    val severity: String,       // critical | high | medium | low
    val device: String,         // тип техники
    val symptoms: List<String>,
    val causes: List<Cause>,
    val diagnostics: List<Action>,
    val fix: List<Action>,
    val tools: List<String>,
    val warnings: List<String>,
    val images: List<String>,
    val timeEstimate: String,
)

data class Cause(val text: String, val check: String)

/** Шаг инструкции с заголовком и подробным описанием. */
data class Action(val title: String, val detail: String)