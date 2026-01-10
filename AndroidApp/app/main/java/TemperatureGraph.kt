package com.example.iot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Константы для графика температуры.
 */
private object GraphConstants {
    /** Минимальный масштаб графика */
    const val MIN_SCALE = 0.5f

    /** Максимальный масштаб графика */
    const val MAX_SCALE = 10f

    /** Минимальная температура на графике (°C) */
    const val MIN_TEMPERATURE = 0

    /** Максимальная температура на графика (°C) */
    const val MAX_TEMPERATURE = 1500

    /** Шаг температурных линий (°C) */
    const val TEMPERATURE_STEP = 50

    /** Шаг для основных температурных линий (°C) */
    const val TEMPERATURE_MAJOR_STEP = 100

    /** Шаг времени для генерации временного диапазона (мс) - 1 минута */
    const val TIME_STEP_MS = 60_000L

    /** Порог масштаба для изменения формата времени */
    const val TIME_FORMAT_SCALE_THRESHOLD = 1.5f

    /** Шаг времени для уменьшенного масштаба (минуты) - 1 день */
    const val TIME_STEP_LOW_SCALE = 1440

    /** Шаг времени для увеличенного масштаба (минуты) - 1 час */
    const val TIME_STEP_HIGH_SCALE = 60

    /** Отступ для оси времени от левого края (px) */
    const val TIME_AXIS_OFFSET_X = 100f

    /** Отступ от края для подписей температуры (px) */
    const val TEMPERATURE_LABEL_OFFSET_X = 10f

    /** Коэффициент использования высоты для температурной шкалы */
    const val TEMPERATURE_SCALE_FACTOR = 0.95f

    /** Температура для оранжевого цвета (°C) */
    const val ORANGE_TEMPERATURE = 750

    /** Формат даты и времени для парсинга */
    const val DATE_FORMAT = "dd.MM.yyyy HH:mm"

    /** Формат времени для увеличенного масштаба */
    const val TIME_FORMAT_HIGH_SCALE = "EEE HH:mm"

    /** Формат времени для уменьшенного масштаба */
    const val TIME_FORMAT_LOW_SCALE = "MM.dd"
}

/**
 * Интерактивный график температуры с поддержкой масштабирования и прокрутки.
 *
 * Отображает историю температуры в виде графика с цветовой индикацией,
 * временной осью и масштабированием от одного часа до нескольких дней.
 *
 * **Возможности:**
 * - Масштабирование жестами (pinch-to-zoom)
 * - Прокрутка графика (pan)
 * - Цветовая индикация температуры (зеленый → оранжевый → красный)
 * - Адаптивное форматирование времени в зависимости от масштаба
 * - Сетка с подписями температур и временных меток
 *
 * @param modifier Модификатор для настройки размеров и позиции графика
 * @param temperatureData Список данных о температуре с временными метками
 *
 * **Формат данных:**
 * Данные должны быть отсортированы по дате и содержать поля:
 * - `date`: строка в формате "dd.MM.yyyy HH:mm"
 * - `temperature`: температура в градусах Цельсия
 *
 * @author Ремянников Валентин Владимирович
 * @author Котов Алексей Валерьевич
 */
@Composable
fun TemperatureGraph(
    modifier: Modifier = Modifier,
    temperatureData: List<TemperatureData>
) {
    // Состояние масштабирования и смещения графика
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scrollState = rememberScrollState()

    // Вычисление временного диапазона данных
    val (startTime, endTime) = remember(temperatureData) {
        if (temperatureData.isEmpty()) {
            Pair(0L, 0L)
        } else {
            val firstDate = parseDate(temperatureData.first().date)
            val lastDate = parseDate(temperatureData.last().date)
            Pair(firstDate, lastDate)
        }
    }

    // Генерация временного диапазона для оси времени
    val timeRange = remember(startTime, endTime) {
        if (startTime == 0L || endTime == 0L || startTime >= endTime) {
            emptyList()
        } else {
            (startTime..endTime step GraphConstants.TIME_STEP_MS).toList()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.statusBars)
            .scrollable(
                state = scrollState,
                orientation = androidx.compose.foundation.gestures.Orientation.Horizontal
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Обновление масштаба с ограничениями
                    scale = max(GraphConstants.MIN_SCALE, min(scale * zoom, GraphConstants.MAX_SCALE))
                    
                    // Обновление смещения с ограничениями для горизонтальной прокрутки
                    val newOffsetX = offset.x + pan.x
                    val maxOffsetX = if (timeRange.isNotEmpty()) {
                        (timeRange.size / scale) * GraphConstants.TIME_STEP_MS.toFloat() / 1000f - size.width
                    } else {
                        0f
                    }
                    offset = Offset(
                        x = newOffsetX.coerceIn(-maxOffsetX, 0f),
                        y = offset.y + pan.y
                    )
                }
            }
    ) {
        // Canvas для временной шкалы
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val topPadding = 0f

            // Рисование горизонтальных линий сетки (температурные линии)
            val tempRange = GraphConstants.MIN_TEMPERATURE..GraphConstants.MAX_TEMPERATURE
            val tempScaleHeight = (canvasHeight - topPadding) * GraphConstants.TEMPERATURE_SCALE_FACTOR /
                    (tempRange.last - tempRange.first)

            for (temp in tempRange step GraphConstants.TEMPERATURE_STEP) {
                val y = (tempRange.last - temp) * tempScaleHeight + topPadding
                val lineColor = getTemperatureColor(temp)
                val lineWidth = if (temp % GraphConstants.TEMPERATURE_MAJOR_STEP == 0) 2f else 1f

                drawLine(
                    color = Color(lineColor),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = lineWidth
                )

                // Добавление подписей температур
                if (temp % GraphConstants.TEMPERATURE_MAJOR_STEP == 0) {
                    // Подписи каждые 100 градусов с большим шрифтом
                    drawIntoCanvas { nativeCanvas ->
                        nativeCanvas.nativeCanvas.drawText(
                            "$temp°C",
                            GraphConstants.TEMPERATURE_LABEL_OFFSET_X,
                            y - 10f,
                            android.graphics.Paint().apply {
                                this.color = lineColor
                                textSize = 40f
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                    }
                } else if (temp % GraphConstants.TEMPERATURE_STEP == 0) {
                    // Подписи каждые 50 градусов с меньшим шрифтом
                    drawIntoCanvas { nativeCanvas ->
                        nativeCanvas.nativeCanvas.drawText(
                            "$temp°C",
                            GraphConstants.TEMPERATURE_LABEL_OFFSET_X,
                            y - 10f,
                            android.graphics.Paint().apply {
                                this.color = lineColor
                                textSize = 30f
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                    }
                }
            }

            // Рисование вертикальных линий сетки (временные линии)
            if (timeRange.isNotEmpty()) {
                val timeStep = calculateTimeStep(scale, canvasWidth)
                val timeScaleWidth = (canvasWidth - GraphConstants.TIME_AXIS_OFFSET_X) /
                        (timeRange.size / scale)

                var prevTextEndX = 0f

                for (time in timeRange.indices step timeStep) {
                    val x = time * timeScaleWidth + GraphConstants.TIME_AXIS_OFFSET_X + offset.x

                    // Рисование вертикальной линии
                    if (x >= 0 && x <= canvasWidth) {
                        drawLine(
                            color = Color.LightGray,
                            start = Offset(x, topPadding),
                            end = Offset(x, canvasHeight),
                            strokeWidth = 1f
                        )
                    }

                    // Форматирование времени в зависимости от масштаба
                    val timeText = formatTimeLabel(timeRange[time], scale)
                    val textPaint = android.graphics.Paint().apply {
                        textSize = 50f
                    }
                    val textWidth = textPaint.measureText(timeText)

                    // Проверка достаточности места для отрисовки текста
                    if (x >= prevTextEndX && x >= 0 && x <= canvasWidth) {
                        drawIntoCanvas { nativeCanvas ->
                            nativeCanvas.nativeCanvas.save()
                            nativeCanvas.nativeCanvas.rotate(-25f, x, canvasHeight - 10f)
                            nativeCanvas.nativeCanvas.drawText(
                                timeText,
                                x,
                                canvasHeight - 10f,
                                textPaint.apply {
                                    color = android.graphics.Color.BLACK
                                    textAlign = android.graphics.Paint.Align.LEFT
                                }
                            )
                            nativeCanvas.nativeCanvas.restore()
                        }
                        prevTextEndX = x + textWidth + 30f
                    }
                }
            }
        }

        // Canvas для графика температуры
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val topPadding = 0f // Убираем верхний отступ

            // Рисование графика температуры
            if (temperatureData.isNotEmpty() && timeRange.isNotEmpty() && startTime != 0L) {
                val tempRange = GraphConstants.MIN_TEMPERATURE..GraphConstants.MAX_TEMPERATURE
                val tempScale = (canvasHeight - topPadding) * GraphConstants.TEMPERATURE_SCALE_FACTOR /
                        (tempRange.last - tempRange.first)
                val tempOffset = topPadding
                val timeScaleWidth = (canvasWidth - GraphConstants.TIME_AXIS_OFFSET_X) /
                        (timeRange.size / scale)

                for (i in 0 until temperatureData.size - 1) {
                    val date1 = parseDate(temperatureData[i].date)
                    val date2 = parseDate(temperatureData[i + 1].date)

                    if (date1 > 0L && date2 > 0L) {
                        val x1 = ((date1 - startTime) / GraphConstants.TIME_STEP_MS) * timeScaleWidth +
                                GraphConstants.TIME_AXIS_OFFSET_X + offset.x
                        val y1 = (tempRange.last - temperatureData[i].temperature) * tempScale + tempOffset
                        val x2 = ((date2 - startTime) / GraphConstants.TIME_STEP_MS) * timeScaleWidth +
                                GraphConstants.TIME_AXIS_OFFSET_X + offset.x
                        val y2 = (tempRange.last - temperatureData[i + 1].temperature) * tempScale + tempOffset

                        // Рисование линии только если она видна на экране
                        if ((x1 >= 0 || x2 >= 0) && (x1 <= canvasWidth || x2 <= canvasWidth)) {
                            drawLine(
                                color = Color.Red,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Возвращает цвет в зависимости от температуры.
 *
 * Цветовая шкала:
 * - 0-750°C: от зеленого к оранжевому
 * - 750-1500°C: от оранжевого к красному
 *
 * @param temperature Температура в градусах Цельсия
 * @return Цвет в формате ARGB
 */
private fun getTemperatureColor(temperature: Int): Int {
    return when {
        temperature > GraphConstants.ORANGE_TEMPERATURE -> {
            // От оранжевого к красному (увеличиваем красный, уменьшаем зеленый)
            val tempAboveOrange = temperature - GraphConstants.ORANGE_TEMPERATURE
            val maxTempRange = GraphConstants.MAX_TEMPERATURE - GraphConstants.ORANGE_TEMPERATURE
            val red = 255
            val green = max(0, 128 - ((tempAboveOrange * 128) / maxTempRange).toInt())
            android.graphics.Color.rgb(red, green, 0)
        }
        temperature < GraphConstants.ORANGE_TEMPERATURE -> {
            // От зеленого к оранжевому (увеличиваем красный, уменьшаем зеленый)
            val red = min(255, ((temperature * 255) / GraphConstants.ORANGE_TEMPERATURE).toInt())
            val green = 255 - (red / 2)
            android.graphics.Color.rgb(red, green, 0)
        }
        else -> {
            // Оранжевый цвет для температуры 750°C
            android.graphics.Color.rgb(255, 128, 0)
        }
    }
}

/**
 * Преобразует строку даты в миллисекунды с начала эпохи Unix.
 *
 * @param dateString Строка даты в формате "dd.MM.yyyy HH:mm"
 * @return Время в миллисекундах или 0L при ошибке парсинга
 */
private fun parseDate(dateString: String): Long {
    return try {
        val format = SimpleDateFormat(GraphConstants.DATE_FORMAT, Locale.getDefault())
        format.parse(dateString)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

/**
 * Форматирует временную метку для отображения на оси времени.
 *
 * Формат зависит от масштаба графика:
 * - Малый масштаб (< 1.5): формат "MM.dd" (месяц.день)
 * - Большой масштаб (>= 1.5): формат "EEE HH:mm" (день недели часы:минуты)
 *
 * @param timestamp Временная метка в миллисекундах
 * @param scale Текущий масштаб графика
 * @return Отформатированная строка времени
 */
private fun formatTimeLabel(timestamp: Long, scale: Float): String {
    val date = Date(timestamp)
    val formatPattern = if (scale < GraphConstants.TIME_FORMAT_SCALE_THRESHOLD) {
        GraphConstants.TIME_FORMAT_LOW_SCALE
    } else {
        GraphConstants.TIME_FORMAT_HIGH_SCALE
    }
    return SimpleDateFormat(formatPattern, Locale.getDefault()).format(date)
}

/**
 * Рассчитывает шаг времени для отрисовки временных меток на оси.
 *
 * Шаг зависит от масштаба графика:
 * - Малый масштаб (< 1.5): 1 день (1440 минут)
 * - Большой масштаб (>= 1.5): 1 час (60 минут)
 *
 * @param scale Текущий масштаб графика
 * @param canvasWidth Ширина Canvas (не используется, оставлен для совместимости)
 * @return Шаг времени в минутах
 */
private fun calculateTimeStep(scale: Float, canvasWidth: Float): Int {
    return if (scale < GraphConstants.TIME_FORMAT_SCALE_THRESHOLD) {
        GraphConstants.TIME_STEP_LOW_SCALE  // 1 день
    } else {
        GraphConstants.TIME_STEP_HIGH_SCALE  // 1 час
    }
}
