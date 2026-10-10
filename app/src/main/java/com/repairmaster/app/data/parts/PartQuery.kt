package com.repairmaster.app.data.parts

/** Один вариант поискового запроса, который уйдёт в магазин. */
data class QueryVariant(
    val text: String,
    val weight: Double,
    /** Короткое пояснение для интерфейса: откуда взялся вариант. */
    val label: String,
)

/**
 * Разбор запроса мастера: что за устройство, какая деталь, какая маркировка.
 * Именно из этого строятся варианты запроса — разные площадки по-разному
 * относятся к длинным («Philips HR1858 ремень») и коротким («1N4007») строкам.
 */
data class PartQuery(
    val raw: String,
    val marking: String?,
    val model: String?,
    val brand: String?,
    val partTerm: String?,
    val params: List<String>,
    val variants: List<QueryVariant>,
) {
    /** Уникальные варианты по убыванию веса. */
    val queries: List<String>
        get() = variants.map { it.text }.distinct()

    /** Самый перспективный запрос. */
    val best: String
        get() = variants.maxByOrNull { it.weight }?.text ?: raw.trim()
}

/**
 * Разбор свободного запроса без словарей «в одну строку»: сначала модели, потом
 * детали, потом маркировки. Всё работает офлайн, сеть не нужна.
 *
 * Свой список моделей можно дописать в `assets/db/device_models.json`:
 * `{ "models": ["Philips HR1858", "Jura E8"] }` — они получат высший приоритет.
 */
class QueryAnalyzer(private val deviceModels: List<String> = DEVICE_MODELS) {

    fun analyze(raw: String): PartQuery {
        val text = raw.trim()
        if (text.isEmpty()) {
            return PartQuery(text, null, null, null, null, emptyList(), emptyList())
        }
        // Нормализация с сохранением длины: индексы совпадают с исходной строкой.
        val norm = normalizeKeepLen(text)

        val modelHit = findModel(text, norm)
        val partHit = findPartHit(norm, modelHit?.third ?: 0)
        val marking = findMarking(text, norm, modelHit?.first, partHit?.start ?: -1)
        val params = PARAM_PATTERNS
            .flatMap { re -> re.findAll(norm).map { it.value.trim() } }
            .distinct()

        val variants = buildVariants(text, marking, modelHit?.first, partHit?.term)
        return PartQuery(
            raw = text,
            marking = marking,
            model = modelHit?.first,
            brand = modelHit?.second,
            partTerm = partHit?.term,
            params = params,
            variants = variants,
        )
    }

    private class PartHit(val term: String, val start: Int)

    /** Модель = бренд + код. Код берём до начала названия детали или до конца строки. */    private fun findModel(raw: String, norm: String): Triple<String, String, Int>? {
        for (brand in BRANDS.sortedByDescending { it.length }) {
            val re = Regex("(?<![a-zа-я0-9])" + Regex.escape(brand) + "(?![a-zа-я0-9])")
            val m = re.find(norm) ?: continue
            val after = m.range.last + 1
            val limit = findPartHit(norm, after)?.start ?: norm.length
            if (after >= raw.length) continue
            val head = raw.substring(after, minOf(limit, raw.length))
                .replace(Regex("[^\\p{L}\\p{N}\\-. /]"), " ")
                .trim()
            if (head.isBlank()) continue
            // Код модели либо содержит цифру («HR1858», «A50»), либо написан с
            // заглавной буквы («Giga», «Dedica»). Обычное слово со строчной
            // буквы — это уже название детали («венчик») или лишний текст.
            val tokens = head.split(Regex("\\s+"))
                .filter { it.isNotBlank() && isCodeLike(it) && !isStopModelToken(it) }
                .take(3)
            if (tokens.isEmpty()) continue
            val brandWord = raw.substring(m.range.first, after).trim()
            val model = (brandWord + " " + tokens.joinToString(" ")).trim()
            if (model.length < brandWord.length + 2) continue
            return Triple(model, brandWord, m.range.first)
        }
        return null
    }

    /** Название детали: список отсортирован от точных слов к общим. */
    private fun findPartHit(norm: String, from: Int): PartHit? {
        if (from >= norm.length) return null
        for ((re, canonical) in PART_TERMS) {
            val m = re.find(norm, from) ?: continue
            return PartHit(canonical, m.range.first)
        }
        return null
    }

    /** Маркировка детали: 1N4007, L7805CV, 14D471K, 6205, 0805. */
    private fun findMarking(
        raw: String,
        norm: String,
        model: String?,
        partStart: Int,
    ): String? {
        val scope = if (partStart in 1 until norm.length) partStart else norm.length
        val head = raw.substring(0, minOf(scope, raw.length))
        for (re in MARKING_PATTERNS) {
            val m = re.find(head) ?: continue
            val value = m.value.trim().uppercase()
            if (value.length < 3) continue
            // Код модели («HR1858» из «Philips HR1858») маркировкой не считаем.
            if (model != null && model.contains(value, true)) continue
            if (model.let { it != null && it.equals(value, true) }) continue
            return value
        }
        return null
    }

    private fun buildVariants(
        raw: String,
        marking: String?,
        model: String?,
        part: String?,
    ): List<QueryVariant> {
        val out = ArrayList<QueryVariant>()
        fun add(text: String, weight: Double, label: String) {
            val t = text.trim().replace(Regex("\\s+"), " ")
            if (t.isNotBlank()) out += QueryVariant(t, weight, label)
        }

        if (model != null && part != null) {
            add("$model $part", 1.0, "модель + деталь")
            add("$part для $model", 0.85, "деталь для модели")
            add("$model $part запчасть", 0.75, "запчасть под модель")
            add(model, 0.4, "только модель")
        }
        if (marking != null) {
            add(marking, 1.0, "маркировка")
            if (part != null) add("$marking $part", 0.9, "маркировка + деталь")
            if (model != null) add("$marking $model", 0.8, "маркировка + модель")
        }
        if (part != null) add(part, 0.45, "только деталь")
        if (marking != null && model != null && part != null) {
            add("$marking $model $part", 0.7, "полный запрос")
        }
        add(raw, 0.5, "как введено")

        return out.distinctBy { it.text.lowercase() }
            .sortedByDescending { it.weight }
    }

    private fun isStopModelToken(t: String): Boolean {
        val low = t.lowercase().trim('(', ')', ',', '.', ':', ';')
        return low.length <= 1 ||
            STOP_MODEL_TOKENS.contains(low) ||
            low.all { !it.isLetterOrDigit() }
    }

    /** Признак кода модели: цифра внутри или заглавная буква в коротком слове. */
    private fun isCodeLike(t: String): Boolean {
        val clean = t.trim('(', ')', ',', '.', ':', ';', '-', '/')
        if (clean.isEmpty()) return false
        return clean.any { it.isDigit() } ||
            (clean.length >= 3 && clean.first().isUpperCase())
    }

    companion object {

        /** Бренды, после которых обычно идёт код модели. */
        private val BRANDS = listOf(
            "iphone", "ipad", "ipod", "apple watch", "airpods", "macbook", "imac", "apple",
            "samsung", "galaxy", "xiaomi", "redmi", "poco", "huawei", "honor", "tecno",
            "infinix", "realme", "oneplus", "oppo", "vivo", "nokia", "meizu", "zte", "itel",
            "philips", "bosch", "siemens", "delonghi", "de longhi", "jura", "kitfort",
            "braun", "tefal", "rowenta", "moulinex", "polaris", "scarlett", "vitek",
            "redmond", "oursson", "centek", "bork", "panasonic", "sharp", "hansa", "beko",
            "electrolux", "indesit", "ariston", "candy", "atlant", "stinol", "miele", "lg",
            "daewoo", "whirlpool", "hotpoint", "karcher", "dyson", "zelmer", "supra", "bbk",
            "tcl", "hisense", "skyworth", "jvc", "toshiba", "grundfos", "wilo", "pedrollo",
            "aquario", "keenetic", "zyxel", "tp-link", "d-link", "mikrotik", "ubiquiti",
            "netgear", "dell", "acer", "lenovo", "msi", "sony", "packard", "depo", "fujitsu",
            "asusus", "nintendo", "playstation",
        )

        private val STOP_MODEL_TOKENS = setOf(
            "для", "и", "в", "с", "на", "из", "под", "запчасть", "запчасти", "оригинал",
            "оригинальный", "ремонт", "мастер", "куплю", "продам", "цена", "не", "или",
            "the", "for", "with", "and",
        )

        /** Детали: точные формулировки первыми, общие — дальше. */
        private val PART_TERMS: List<Pair<Regex, String>> = listOf(
            Regex("тачскрин|дисплейн\\w* модул\\w*|сенсорн\\w* стекл\\w*|сенсорн\\w* экран\\w*") to "Дисплей (тачскрин)",
            Regex("дисплей|экран|матриц\\w*|жк-модул\\w*|lcd|oled") to "Дисплей",
            Regex("стекло защитн\\w*|защитн\\w* стекл\\w*|стекло") to "Стекло защитное",
            Regex("шлейф") to "Шлейф",
            Regex("акб|аккумулятор\\w*|батаре\\w*|батарейк\\w*|battery") to "Аккумулятор",
            Regex("блок питания|источник питания|сетевой адаптер|адаптер|зарядк\\w*|зарядное|бп\\b|psu|charger") to "Блок питания",
            Regex("микросхем\\w*|микроконтроллер\\w*|микропроцессор\\w*|процессор|контроллер\\w*|операционный усилитель\\w*|стабилизатор\\w*|чип\\b|драйвер|ic\\b") to "Микросхема",
            Regex("конденсатор\\w*|электролитическ\\w*|capacitor") to "Конденсатор",
            Regex("резистор\\w*|подстроечник\\w*|перемычк\\w*|resistor") to "Резистор",
            Regex("варистор\\w*|тиристор\\w*|симистор\\w*|троцлер|triac") to "Симистор/варистор",
            Regex("диодн\\w* мост\\w*|сборка диодн\\w*|диод\\w*|выпрямител\\w*|diode") to "Диод",
            Regex("транзистор\\w*|транзисторы|transistor") to "Транзистор",
            Regex("оптрон\\w*|оптопар\\w*|фототранзистор\\w*|фотодиод\\w*") to "Оптоэлектроника",
            Regex("термостат\\w*|регулятор температуры|термопредохранител\\w*|терморегулятор\\w*") to "Термостат",
            Regex("предохранител\\w*|плавкая вставка|автомат защит\\w*|fuse") to "Предохранитель",
            Regex("разъем|разъём|коннектор\\w*|розетк\\w*|вилк\\w*|клемм\\w*|колодк\\w*|connector") to "Разъём",
            Regex("кнопк\\w*|тумблер\\w*|выключател\\w*|переключател\\w*|микропереключател\\w*|джойстик\\w*|switch") to "Кнопка/выключатель",
            Regex("клавиатур\\w*|keyboard") to "Клавиатура",
            Regex("ремен\\w*|ремни|belt") to "Ремень",
            Regex("подшипник\\w*|bearing") to "Подшипник",
            Regex("уплотнител\\w*|прокладк\\w*|манжет\\w*|сальник\\w*|seal|gasket") to "Уплотнитель",
            Regex("шланг\\w*|патрубок\\w*|трубк\\w*|hose") to "Шланг",
            Regex("насос\\w*|помп\\w*|pump") to "Насос",
            Regex("тэн\\b|тэновый|нагревательн\\w* элемент\\w*|нагревател\\w*|heater") to "Нагревательный элемент (ТЭН)",
            Regex("мотор\\w*|двигател\\w*|электродвигател\\w*|коллекторн\\w* мотор\\w*|motor") to "Двигатель",
            Regex("щетк\\w*|щётк\\w*|brush") to "Щётка",
            Regex("фильтр\\w*|filter") to "Фильтр",
            Regex("вентилятор\\w*|кулер\\w*|fan\\b") to "Вентилятор",
            Regex("радиатор\\w*|теплоотвод\\w*") to "Радиатор",
            Regex("магнетрон\\w*|magnetron") to "Магнетрон",
            Regex("камер\\w*|camera") to "Камера",
            Regex("динамик\\w*|звуковая катушка|сабвуфер\\w*|speaker") to "Динамик",
            Regex("микрофон\\w*|microphone") to "Микрофон",
            Regex("корпус\\w*|крышк\\w*|панел\\w*|дверц\\w*|ручк\\w*|housing") to "Корпус",
            Regex("лампочк\\w*|лампа\\b|светодиод\\w*|led-модул\\w*|led\\b|подсветк\\w*") to "Лампа/светодиод",
            Regex("плата\\b|плата управлени\\w*|блок управлени\\w*|модуль управлени\\w*|board") to "Плата управления",
            Regex("трансформатор\\w*|инвертор\\w*|балласт\\w*|источник питани\\w*|transformer") to "Трансформатор/драйвер",
            Regex("реле\\w*|контактор\\w*|relay") to "Реле",
            Regex("клапан\\w*|вентиль\\w*|соленоид\\w*|катушка соленоида|valve") to "Клапан/соленоид",
            Regex("датчик\\w*|сенсор\\w*|термистор\\w*|терморезистор\\w*|sensor") to "Датчик",
            Regex("редуктор\\w*|муфт\\w*|пружин\\w*|шток\\w*|поршен\\w*|маховик\\w*|шкив\\w*") to "Механическая деталь",
            Regex("антенн\\w*|antenna") to "Антенна",
            Regex("нож\\w*|ножи|лезви\\w*|blade") to "Ножи",
            Regex("венчик\\w*|насадк\\w*|насадка|бленд\\w*|терк\\w*|диск|\bчаш\\w*|кувшин\\w*") to "Насадка/венчик",
            Regex("мешок\\w*|контейнер\\w*|циклон\\w*|фильтр-мешок") to "Аксессуар",
            Regex("паст\\w*|флюс\\w*|припо\\w*|жало\\w*|расходник\\w*") to "Расходник",
            Regex("процессор|cpu|видеокарт\\w*|gpu|оперативная памят\\w*|модуль памя\\w*") to "Модуль для ПК",
            Regex("экран\\w* диспле\\w*|модул\\w* диспле\\w*") to "Дисплей",
        )

        /** Маркировки деталей — по убыванию характерности. */
        private val MARKING_PATTERNS = listOf(
            Regex("""(?<![A-Za-zА-Яа-я0-9])\d[A-ZА-Я]{1,3}\d{3,4}[A-ZА-Я]{0,3}\d{0,4}(?![A-Za-zА-Яа-я0-9])"""),
            Regex("""(?<![A-Za-zА-Яа-я0-9])[A-ZА-Я]{1,4}[-/]?\d{3,5}[A-ZА-Я]{0,4}[-]?\d{0,4}(?![A-Za-zА-Яа-я0-9])"""),
            Regex("""(?<![A-Za-zА-Яа-я0-9])[A-ZА-Я]{1,3}\d{2,4}[A-ZА-Я]\d{0,3}(?![A-Za-zА-Яа-я0-9])"""),
            // Чисто цифровые маркировки: 7805, 7812, 7912 — у радио деталей сплошь и рядом.
            Regex("""(?<![A-Za-zА-Яа-я0-9])\d{4,5}(?![A-Za-zА-Яа-я0-9])"""),
            Regex("""(?<![0-9])(?:6[0-2]\d{2}|608|6203)(?![0-9])"""),
            Regex("""(?<![A-Za-z0-9])(?:0201|0402|0603|0805|1206|1210|1812|2010|2512)(?![0-9])"""),
            Regex("""(?<![A-Za-z0-9])SOT-?(?:23|89|223|323)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
            Regex("""(?<![A-Za-z0-9])TO-?(?:220|247|263|126|92)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
            Regex("""(?<![A-Za-z0-9])(?:DO-?41|DO-?35|DIP-?8|DIP-?16|SMA|SMB)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE),
        )

        /** Параметры детали: ёмкость, сопротивление, напряжение, корпус. */
        private val PARAM_PATTERNS = listOf(
            Regex("""\d+(?:[.,]\d+)?\s*(?:нФ|нф|пФ|пф|мкФ|мкф|мФ|мф|Ф|uF|uf|uF|pF)(?![A-Za-zА-Яа-я])"""),
            Regex("""\d+(?:[.,]\d+)?\s*(?:Ом|ом|кОм|ком|МОм|мом|kOm|kOm)(?![A-Za-zА-Яа-я])"""),
            Regex("""\d+(?:[.,]\d+)?\s*[ВвVv](?![A-Za-zА-Яа-я0-9])"""),
            Regex("""\d+\s*(?:мА|А|МА)(?![A-Za-zА-Яа-я0-9])"""),
        )

        /** Нормализация с сохранением длины — индексы можно переносить в исходную строку. */
        fun normalizeKeepLen(s: String): String {
            val sb = StringBuilder(s.length)
            for (ch in s) {
                val low = when (ch) {
                    'Ё', 'ё' -> 'е'
                    else -> ch.lowercaseChar()
                }
                sb.append(
                    if (low.isLetterOrDigit() || low == '.' || low == '+' || low == '-') low else ' '
                )
            }
            return sb.toString()
        }

        /** Модели устройств, которые встречаются в справочнике и в реальном ремонте. */
        val DEVICE_MODELS = listOf(
            "Philips HR1858", "Philips HR1864", "Bosch MUC2", "Bosch MUM", "Kitfort",
            "Jura E8", "Jura S8", "De'Longhi ECAM", "De'Longhi Dedica",
            "Keenetic Giga", "Keenetic Viva", "Keenetic 4G", "Keenetic II",
            "Zyxel Keenetic", "Kitfort KT",
            "iPhone", "iPad", "Samsung Galaxy", "Xiaomi Redmi", "Xiaomi Poco",
            "Huawei Honor", "Tecno", "Infinix", "Realme", "OnePlus", "OPPO", "Vivo",
            "Nokia", "LG", "Samsung UE", "TCL", "Hisense", "Xiaomi MI TV",
            "Bosch Maxx", "Siemens", "Electrolux", "Indesit", "Ariston", "Beko",
            "Atlant", "Whirlpool", "Karcher", "Dyson", "Polaris", "Scarlett",
            "Vitek", "Redmond", "Tefal", "Rowenta", "Braun", "Moulinex", "Oursson",
            "Centek", "Bork", "Panasonic", "Daewoo", "MacBook", "Lenovo", "ASUS",
            "Acer", "HP", "Dell", "MSI", "Wilo", "Grundfos",
        )
    }
}
