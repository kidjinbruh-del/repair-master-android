package com.repairmaster.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repairmaster.app.ui.theme.Amber
import com.repairmaster.app.ui.theme.Cyan
import com.repairmaster.app.ui.theme.Green
import com.repairmaster.app.ui.theme.Line
import com.repairmaster.app.ui.theme.Panel
import com.repairmaster.app.ui.theme.Panel2
import com.repairmaster.app.ui.theme.TextDim
import java.util.Locale
import kotlin.math.abs

/**
 * Калькулятор по базовой теории: закон Ома, токоограничительный резистор для
 * светодиода, делитель напряжения и соединение сопротивлений.
 * Считает на лету, полностью офлайн.
 */
@Composable
fun CalculatorScreen() {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Закон Ома", "Светодиод", "Делитель", "Соединения")

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                "Калькулятор",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Считает по основным единицам: В, А, Ом. Заполните два поля — третье посчитаем.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
            )
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { i, title ->
                val active = i == tab
                Box(
                    Modifier
                        .background(
                            if (active) Amber.copy(alpha = 0.18f) else Panel2,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { tab = i }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Amber else TextDim,
                    )
                }
            }
        }

        when (tab) {
            0 -> OhmTool()
            1 -> LedTool()
            2 -> DividerTool()
            else -> CombineTool()
        }
    }
}

// ------------------------------------------------------------------ инструменты

@Composable
private fun OhmTool() {
    var uStr by remember { mutableStateOf("") }
    var iStr by remember { mutableStateOf("") }
    var rStr by remember { mutableStateOf("") }
    val u = num(uStr)
    val i = num(iStr)
    val r = num(rStr)

    val lines = mutableListOf<Out>()
    var error: String? = null

    when {
        u != null && r != null && i == null -> {
            if (r == 0.0) error = "Сопротивление не может быть нулём — проверьте значение"
            else lines += Out("I = U / R", ampStr(u / r), "сила тока через резистор")
        }

        u != null && i != null && r == null -> {
            if (i == 0.0) error = "Нулевой ток не даёт конечного сопротивления"
            else lines += Out("R = U / I", ohmStr(u / i), "сопротивление")
        }

        i != null && r != null && u == null -> {
            if (r == 0.0) error = "Сопротивление не может быть нулём — проверьте значение"
            else lines += Out("U = I × R", voltStr(i * r), "напряжение на резисторе")
        }

        u != null && i != null && r != null -> {
            if (r == 0.0) error = "Сопротивление не может быть нулём — проверьте значение"
            else {
                lines += Out("R = U / I", ohmStr(u / i), "сопротивление")
                lines += Out("I = U / R", ampStr(u / r), "сила тока")
                lines += Out("U = I × R", voltStr(i * r), "напряжение")
            }
        }

        else -> error = "Заполните любые две величины"
    }
    if (u != null && i != null) lines += Out("P = U × I", wattStr(u * i), "мощность")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("U, вольт", uStr, { uStr = it }, Modifier.weight(1f))
            Field("I, ампер", iStr, { iStr = it }, Modifier.weight(1f))
            Field("R, ом", rStr, { rStr = it }, Modifier.weight(1f))
        }
        Result(lines, error)
        Hint(
            "Опорные значения: светодиод 2–3 В и 0,02 А; лампа 220 В и 0,27 А; " +
                "компьютерный БП 12 В и 3–6 А. Калькулятор ждёт величины в основных единицах.",
        )
    }
}

@Composable
private fun LedTool() {
    var usStr by remember { mutableStateOf("5") }
    var ulStr by remember { mutableStateOf("2") }
    var iStr by remember { mutableStateOf("0,02") }
    val us = num(usStr)
    val ul = num(ulStr)
    val cur = num(iStr)

    val lines = mutableListOf<Out>()
    var error: String? = null

    if (us != null && ul != null && cur != null) {
        val d = us - ul
        when {
            us <= 0 -> error = "Напряжение питания должно быть больше нуля"
            ul >= us -> error = "Падение на светодиоде не может быть больше питания"
            cur <= 0 -> error = "Ток должен быть больше нуля"
            else -> {
                val res = d / cur
                val pRes = d * d / res
                lines += Out("R = (U − Uled) / I", ohmStr(res), "токограничительный резистор")
                lines += Out("P на резисторе", wattStr(pRes), "выберите номинал с запасом ×2")
                lines += Out("P на светодиоде", wattStr(ul * cur), "рассеиваемая мощность LED")
                if (pRes > 0.5) {
                    error = "Мощность больше 0,5 Вт — нужен резистор на 1 Вт или выше"
                }
            }
        }
    } else {
        error = "Заполните все три поля"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Uпит, В", usStr, { usStr = it }, Modifier.weight(1f))
            Field("Uled, В", ulStr, { ulStr = it }, Modifier.weight(1f))
            Field("I, А", iStr, { iStr = it }, Modifier.weight(1f))
        }
        Result(lines, error)
        Hint(
            "Падение напряжения: красный 2 В, жёлтый 2,1 В, зелёный и синий 2,2–3,2 В, " +
                "белый 3 В. Ток: индикаторный 5–15 мА, фонарик 20–100 мА. " +
                "Резистор всегда ставьте по мощности с запасом ×2.",
        )
    }
}

@Composable
private fun DividerTool() {
    var uinStr by remember { mutableStateOf("12") }
    var uoutStr by remember { mutableStateOf("3,3") }
    var r1Str by remember { mutableStateOf("27000") }
    val uin = num(uinStr)
    val uout = num(uoutStr)
    val r1 = num(r1Str)

    val lines = mutableListOf<Out>()
    var error: String? = null

    if (uin != null && uout != null && r1 != null) {
        val d = uin - uout
        when {
            r1 <= 0 -> error = "Сопротивление должно быть больше нуля"
            uout <= 0 || uout >= uin ->
                error = "Выходное напряжение должно быть от 0 до входного"

            else -> {
                val r2 = r1 * uout / d
                lines += Out("R2 = R1 × Uвых / (Uвх − Uвых)", ohmStr(r2), "нижний резистор делителя")
                lines += Out("Отношение Uвх / Uвых", plain(r1 / r2), "во столько раз вход больше выхода")
                lines += Out("Ток через делитель", ampStr(uout / r2), "без нагрузки на выходе")
                lines += Out("P на R2", wattStr(uout * uout / r2), "мощность нижнего резистора")
                lines += Out("P на R1", wattStr(d * d / r1), "мощность верхнего резистора")
            }
        }
    } else {
        error = "Заполните все три поля"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Uвх, В", uinStr, { uinStr = it }, Modifier.weight(1f))
            Field("Uвых, В", uoutStr, { uoutStr = it }, Modifier.weight(1f))
        }
        Field("R1, ом", r1Str, { r1Str = it })
        Result(lines, error)
        Hint(
            "Делитель — два резистора последовательно. Нагрузка на выходе меняет Uвых: " +
                "чем меньше входное сопротивление следующего каскада, тем сильнее просадка. " +
                "Подберите R1 и R2 из стандартного ряда (E24), считая по максимальному напряжению.",
        )
    }
}

@Composable
private fun CombineTool() {
    var aStr by remember { mutableStateOf("100") }
    var bStr by remember { mutableStateOf("100") }
    val a = num(aStr)
    val b = num(bStr)

    val lines = mutableListOf<Out>()
    var error: String? = null
    if (a != null && b != null) {
        if (a <= 0 || b <= 0) {
            error = "Сопротивления должны быть больше нуля"
        } else {
            lines += Out("Последовательно: R1 + R2", ohmStr(a + b), "больше любого из двух")
            lines += Out("Параллельно: R1 × R2 / (R1 + R2)", ohmStr(a * b / (a + b)), "меньше наименьшего")
            if (abs(a - b) < 1e-9) {
                lines += Out("Два одинаковых параллельно", ohmStr(a / 2), "ровно половина: 100 Ом + 100 Ом = 50 Ом")
            }
        }
    } else {
        error = "Заполните оба сопротивления"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("R1, ом", aStr, { aStr = it }, Modifier.weight(1f))
            Field("R2, ом", bStr, { bStr = it }, Modifier.weight(1f))
        }
        Result(lines, error)
        Hint(
            "Последовательное соединение складывается, параллельное — всегда меньше " +
                "наименьшего резистора. Частая ошибка: 100 Ом и 100 Ом параллельно дают 50 Ом, " +
                "а не 200 Ом.",
        )
    }
}

// ------------------------------------------------------------------ элементы

private data class Out(val label: String, val value: String, val note: String? = null)

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { s ->
            val ok = s.count { it == '.' || it == ',' } <= 1
            if (ok) onChange(s.filter { it.isDigit() || it == '.' || it == ',' })
        },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Amber,
            unfocusedBorderColor = Line,
            focusedLabelColor = Amber,
            unfocusedLabelColor = TextDim,
            cursorColor = Amber,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier,
    )
}

@Composable
private fun Result(lines: List<Out>, error: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = Amber,
            )
        }
        lines.forEach { l ->
            Column {
                Text(l.label, style = MaterialTheme.typography.labelSmall, color = TextDim)
                Text(
                    l.value,
                    style = MaterialTheme.typography.titleLarge,
                    color = Green,
                )
                if (l.note != null) {
                    Text(l.note, style = MaterialTheme.typography.bodySmall, color = TextDim)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel2, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text("ПОДСКАЗКА", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

// ------------------------------------------------------------------ числа

private fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Формат с запятой как десятичным знаком и без хвостовых нулей. */
private fun trim(s: String): String {
    val t = s.replace('.', ',')
    if (!t.contains(',')) return t
    return t.trimEnd('0').trimEnd(',').ifEmpty { "0" }
}

private fun f(v: Double, unit: String): String = trim(String.format(Locale.US, "%.4g", v)) + unit

private fun plain(v: Double): String = trim(String.format(Locale.US, "%.4g", v))

private fun ohmStr(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1e6 -> f(v / 1e6, " МОм")
        a >= 1e3 -> f(v / 1e3, " кОм")
        else -> f(v, " Ом")
    }
}

private fun ampStr(v: Double): String {
    val a = abs(v)
    return when {
        a >= 1 -> f(v, " А")
        a >= 1e-3 -> f(v * 1e3, " мА")
        else -> f(v * 1e6, " мкА")
    }
}

private fun voltStr(v: Double): String {
    val a = abs(v)
    return if (a >= 1000) f(v / 1e3, " кВ") else f(v, " В")
}

private fun wattStr(v: Double): String {
    val a = abs(v)
    return if (a >= 1000) f(v / 1e3, " кВт") else f(v, " Вт")
}