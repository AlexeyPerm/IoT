package com.example.iot

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import java.util.Locale
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars

@Composable
fun TemperatureGraph(
    modifier: Modifier = Modifier,
    temperatureData: List<TemperatureData>
) {
    var scale by remember { mutableStateOf(1f) } // Масштаб графика
    var offset by remember { mutableStateOf(Offset.Zero) } // Смещение графика
    val scrollState = rememberScrollState()

    // Получаем временной диапазон (первая и последняя дата)
    val (startTime, endTime) = remember(temperatureData) {
        if (temperatureData.isEmpty()) {
            Pair(0L, 0L)
        } else {
            val firstDate = parseDate(temperatureData.first().date)
            val lastDate = parseDate(temperatureData.last().date)
            Pair(firstDate, lastDate)
        }
    }

    // Генерация временного диапазона
    val timeRange = remember(startTime, endTime) {
        if (startTime == 0L || endTime == 0L) {
            emptyList()
        } else {
            (startTime..endTime step 60_000L).toList() // Шаг в 1 минуту
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
                    // Обновляем масштаб и смещение с ограничением на минимальный масштаб
                    scale = max(0.5f, min(scale * zoom, 10f)) // Минимальный масштаб 0.5
                    val newOffsetX = offset.x + pan.x
                    val maxOffsetX = (timeRange.size / scale) * 60f - size.width
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

            // Рисуем горизонтальные линии сетки (температурные линии)
            val tempRange = 0..1500
            val tempScaleHeight = (canvasHeight - topPadding) * 0.95f / (tempRange.last - tempRange.first)

            for (temp in tempRange step 50) { // Шаг 50 градусов
                val y = (tempRange.last - temp) * tempScaleHeight + topPadding
                val lineColor = getTemperatureColor(temp)
                val lineWidth = if (temp % 100 == 0) 2f else 1f

                drawLine(
                    color = Color(lineColor),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = lineWidth
                )

                // Добавляем подписи температур
                if (temp % 100 == 0) {
                    // Подписи каждые 100 градусов с большим шрифтом
                    drawIntoCanvas { nativeCanvas ->
                        nativeCanvas.nativeCanvas.drawText(
                            "$temp°C",
                            10f, // Отступ слева
                            y - 10f, // Смещение вверх для текста
                            android.graphics.Paint().apply {
                                color = lineColor // Используем цвет линии
                                textSize = 40f // Большой шрифт
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                    }
                } else if (temp % 50 == 0) {
                    // Подписи каждые 50 градусов с меньшим шрифтом
                    drawIntoCanvas { nativeCanvas ->
                        nativeCanvas.nativeCanvas.drawText(
                            "$temp°C",
                            10f, // Отступ слева
                            y - 10f, // Смещение вверх для текста
                            android.graphics.Paint().apply {
                                color = lineColor // Используем цвет линии
                                textSize = 30f // Меньший шрифт
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                    }
                }
            }

            // Рисуем вертикальные линии сетки (временные линии)
            val timeStep = calculateTimeStep(scale, canvasWidth)
            val timeAxisOffsetX = 100f
            val timeScaleWidth = (canvasWidth - timeAxisOffsetX) / (timeRange.size / scale)

            var prevTextEndX = 0f

            for (time in timeRange.indices step timeStep) {
                val x = time * timeScaleWidth + timeAxisOffsetX + offset.x

                drawLine(
                    color = Color.LightGray,
                    start = Offset(x, topPadding),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1f
                )

                // Форматируем время в зависимости от масштаба
                val timeText = when {
                    scale < 1.5f -> {
                        // Формат "месяц.день" для уменьшенного масштаба
                        val date = Date(timeRange[time])
                        SimpleDateFormat("MM.dd", Locale.getDefault()).format(date)
                    }
                    else -> {
                        // Формат "день недели часы:минуты" для увеличенного масштаба
                        val date = Date(timeRange[time])
                        SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(date)
                    }
                }

                val textPaint = android.graphics.Paint().apply {
                    textSize = 50f
                }
                val textWidth = textPaint.measureText(timeText)

                // Проверяем, достаточно ли места для отрисовки текста
                if (x >= prevTextEndX) {
                    drawIntoCanvas { nativeCanvas ->
                        nativeCanvas.nativeCanvas.save()
                        nativeCanvas.nativeCanvas.rotate(-25f, x, canvasHeight - 10f)
                        nativeCanvas.nativeCanvas.drawText(
                            timeText,
                            x,
                            canvasHeight - 10f, // Размещаем внизу
                            textPaint.apply {
                                color = android.graphics.Color.BLACK
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                        )
                        nativeCanvas.nativeCanvas.restore()
                    }
                    prevTextEndX = x + textWidth + 30f // Добавляем фиксированное расстояние
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

            // Рисуем график температуры
            val tempRange = 0..1500
            val tempScale = (canvasHeight - topPadding) * 0.95f / (tempRange.last - tempRange.first)
            val tempOffset = topPadding
            val timeAxisOffsetX = 100f
            val timeScaleWidth = (canvasWidth - timeAxisOffsetX) / (timeRange.size / scale)

            for (i in 0 until temperatureData.size - 1) {
                val x1 = (parseDate(temperatureData[i].date) - startTime) / 60_000L * timeScaleWidth + timeAxisOffsetX + offset.x
                val y1 = (tempRange.last - temperatureData[i].temperature) * tempScale + tempOffset
                val x2 = (parseDate(temperatureData[i + 1].date) - startTime) / 60_000L * timeScaleWidth + timeAxisOffsetX + offset.x
                val y2 = (tempRange.last - temperatureData[i + 1].temperature) * tempScale + tempOffset

                drawLine(
                    color = androidx.compose.ui.graphics.Color.Red,
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 2f
                )
            }
        }
    }
}

/**
 * Возвращает цвет в зависимости от температуры.
 */
private fun getTemperatureColor(temperature: Int): Int {
    return when {
        temperature > 750 -> {
            // От оранжевого к красному (увеличиваем красный, уменьшаем зеленый)
            val red = 255
            val green = max(0, 128 - ((temperature - 750) * 0.34).toInt())
            android.graphics.Color.rgb(red, green, 0)
        }
        temperature < 750 -> {
            // От зеленого к оранжевому (увеличиваем красный, уменьшаем зеленый)
            val red = min(255, ((temperature) * 0.34).toInt())
            val green = 255 - (red / 2)
            android.graphics.Color.rgb(red, green, 0)
        }
        else -> {
            // Оранжевый цвет для температуры 750
            android.graphics.Color.rgb(255, 128, 0)
        }
    }
}
/**
 * Преобразование строки даты в миллисекунды.
 */
private fun parseDate(dateString: String): Long {
    val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return format.parse(dateString)?.time ?: 0L
}
/**
Рассчитывает шаг времени в зависимости от масштаба и ширины Canvas.
 */
private fun calculateTimeStep(scale: Float, canvasWidth: Float): Int {
    return when {
        scale < 1.5f -> 1440 // Шаг 1 день для уменьшенного масштаба
        else -> 60 // Шаг 1 час для увеличенного масштаба
    }
}
