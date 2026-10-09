# Мастер по ремонту (RepairMaster)

Офлайн-справочник по ремонту электроники для Android: бытовая, компьютерная и
промышленная техника плюс общие знания. Каждая тема и каждая проблема снабжена
пошаговой инструкцией: симптом → причины → диагностика → ремонт → инструменты →
техника безопасности.

Репозиторий: <https://github.com/kidjinbruh-del/repair-master-android>

## Что внутри

**16 разделов · 52 темы · 9 моделей с разбором · 96 инструкций · 73 иллюстрации**

| Раздел | Тем | Инструкций |
|---|---|---|
| Теория: база электричества | 4 | 5 |
| Бытовая техника | 8 | 17 |
| Электроника и компьютеры | 6 | 12 |
| Промышленная техника | 7 | 11 |
| Вилки, провода, соединения, платы | 5 | 9 |
| Мелкая кухонная техника | 3 | 9 |
| Двигатели, воздуходувки, пневмо | 2 | 4 |
| Сетевая техника | 2 | 4 |
| Безопасность | 1 | 1 |
| Пайка и монтаж | 3 | 4 |
| Мультиметр и измерения | 2 | 4 |
| Компоненты и замена | 3 | 3 |
| Провода и кабели | 2 | 2 |
| Инструменты и рабочее место | 1 | 1 |
| Схемы и узлы | 2 | 2 |
| Диагностика по симптомам | 1 | 7 |

Возможности:
- **калькулятор**: закон Ома (U, I, R, мощность), токоограничительный резистор
  для светодиода, делитель напряжения, соединение сопротивлений;
- базовая теория: величины и единицы, префиксы, закон Ома, соединение
  сопротивлений, AC/DC, ёмкость и индуктивность, фильтры RC и LC;
- поиск по симптомам, компонентам, инструментам и моделям;
- разбор конкретных моделей: Philips HR1858, Bosch MUC2, Kitfort, Jura E8,
  De'Longhi Dedica, Keenetic Giga/Viva/4G, Keenetic II — с конструктивными
  особенностями и своими поломками;
- иллюстрации ко всем ключевым темам, открываются на весь экран с зумом;
- предупреждения по безопасности выделены цветом (опасно / осторожно / совет);
- оценка сложности и времени ремонта у каждой проблемы;
- полностью офлайн, без сети и разрешений.

## Как добавить контент

Контент лежит в `app/src/main/assets/content/*.json` — по одному файлу на группу
тем. Файлы читаются в алфавитном порядке, порядок разделов на главном экране
задаётся полем `group` (`technique` — техника, остальное — справочник).

```json
{
  "categories": [{
    "id": "theory", "title": "Теория", "group": "reference", "icon": "science",
    "summary": "Короткое описание раздела",
    "items": [{
      "id": "theory-ohm", "title": "Закон Ома", "summary": "…",
      "images": ["img/ohm_law.webp"],
      "blocks": [
        {"heading": "Формулы", "type": "table",
         "headers": ["Форма", "Формула"], "rows": [["Ток", "I = U / R"]]},
        {"heading": "Шаги", "type": "steps", "items": ["первый шаг", "второй"]}
      ],
      "warnings": [{"level": "danger", "text": "…"}],
      "problems": [{
        "id": "…", "title": "…", "severity": "low", "device": "…", "time": "20 мин",
        "symptoms": ["…"],
        "causes": [{"text": "…", "check": "как проверить"}],
        "diagnostics": [{"title": "…", "detail": "…"}],
        "fix": [{"title": "…", "detail": "…"}],
        "tools": ["…"], "warnings": ["…"], "images": ["img/…webp"]
      }],
      "models": [{
        "name": "…", "brand": "…", "years": "…", "note": "…",
        "specs": [["Параметр", "Значение"]],
        "problems": [ /* тот же формат, что и problems */ ]
      }]
    }]
  }]
}
```

Код менять не нужно: пересоберите APK. Иллюстрации генерируются кодом:

```powershell
python tools\gen_images.py --only ohm_law   # перегенерировать одну картинку
python tools\gen_images.py                  # все сразу
```

## Сборка

Требуется JDK 17 и Android SDK. Путь к SDK указывается в `local.properties`
(в репозитории не хранится — он привязан к машине):

```properties
sdk.dir=C:/путь/к/Android/Sdk
```

```powershell
# Gradle wrapper сам скачает нужную версию Gradle
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17"
.\gradlew.bat assembleRelease      # APK: app\build\outputs\apk\release\app-release.apk
.\gradlew.bat assembleDebug        # отладочная сборка (applicationId + .debug)
```

Установка на устройство:

```powershell
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Графика

Иллюстрации генерируются кодом (SVG → WebP, 2x) — `tools/`:

```powershell
python tools\gen_images.py                     # все картинки
python tools\gen_images.py --only wire_awg     # одна картинка
```

- `tools\assets.py` — определения всех иллюстраций
- `tools\draw.py` — примитивы (резистор, конденсатор, диод, транзистор,
  мультиметр, паяльник, панели, таблицы)
- `tools\gen_images.py` — рендер через Chromium (Playwright) + Pillow → WebP

Результат: `app\src\main\assets\img\*.webp`.

## Контент

Контент лежит в JSON: `app\src\main\assets\content\*.json` (загружаются все
файлы папки). Формат:

```json
{
  "categories": [{
    "id": "soldering",
    "title": "Пайка и монтаж",
    "icon": "soldering",
    "summary": "...",
    "items": [{
      "id": "solder-basics",
      "title": "Основы пайки",
      "summary": "...",
      "images": ["img/solder_station.webp"],
      "blocks": [
        { "heading": "Пайка: 5 шагов", "type": "steps", "items": ["...", "..."] },
        { "heading": "Припои", "type": "table",
          "headers": ["Припой", "Жало"],
          "rows": [["Sn63Pb37", "300–340 °C"]] }
      ],
      "warnings": [{ "level": "danger", "text": "..." }],
      "problems": [{
        "id": "prob-cold-solder",
        "title": "Холодная пайка",
        "severity": "medium",
        "device": "Любая техника",
        "time": "10–20 мин",
        "symptoms": ["..."],
        "causes": [{ "text": "...", "check": "..." }],
        "diagnostics": [{ "title": "...", "detail": "..." }],
        "fix": [{ "title": "...", "detail": "..." }],
        "tools": ["..."],
        "warnings": ["..."],
        "images": ["img/solder_defects.webp"]
      }]
    }]
  }]
}
```

Добавить тему или проблему = дописать JSON и пересобрать APK (логика не меняется).

## Стек

Kotlin 2.0.21 · Jetpack Compose (Material 3) · minSdk 26 · targetSdk 35 ·
без сторонних UI-библиотек и без сетевых зависимостей.

## Проверено

Сборка → установка → запуск на TECNO BF7 (Android 12): навигация, таблицы,
иллюстрации, инструкции и поиск работают, крэшей в logcat нет.