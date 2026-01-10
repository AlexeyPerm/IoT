package com.example.iot

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException

/**
 * Репозиторий для работы с данными о температуре.
 *
 * Предоставляет методы для чтения данных о температуре из JSON-файла.
 * Используется для загрузки исторических данных для отображения графика.
 *
 * @author Ремянников Валентин Владимирович
 * @author Котов Алексей Валерьевич
 */
object TemperatureDataRepository {

    /** Тег для логирования */
    private const val TAG = "TemperatureDataRepository"

    /** Имя файла с данными о температуре */
    private const val TEMPERATURE_DATA_FILE = "temperature_data.json"

    /** Gson экземпляр для работы с JSON */
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    /**
     * Читает данные о температуре из JSON-файла.
     *
     * Файл находится во внешнем хранилище приложения (getExternalFilesDir).
     * Если файл не существует или пуст, возвращается пустой список.
     *
     * @param context Контекст приложения для доступа к файловой системе
     * @param temperatureDataResourceId ID ресурса (не используется, оставлен для совместимости)
     * @return Список данных о температуре, отсортированный по дате
     *
     * @throws IOException если произошла ошибка при чтении файла
     */
    fun readTemperatureDataFromFile(
        context: Context,
        temperatureDataResourceId: Int
    ): List<TemperatureData> {
        return try {
            val file = File(context.getExternalFilesDir(null), TEMPERATURE_DATA_FILE)

            if (!file.exists() || file.length() == 0) {
                Log.d(TAG, "Файл данных не найден или пуст: ${file.absolutePath}")
                return emptyList()
            }

            val type = object : TypeToken<List<TemperatureData>>() {}.type
            val data = gson.fromJson<List<TemperatureData>>(file.bufferedReader(), type)

            if (data.isNullOrEmpty()) {
                Log.w(TAG, "Файл данных пуст или содержит некорректные данные")
                emptyList()
            } else {
                Log.d(TAG, "Успешно загружено записей: ${data.size}")
                // Сортируем данные по дате для корректного отображения на графике
                data.sortedBy { it.date }
            }
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Ошибка парсинга JSON файла", e)
            emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при чтении файла данных", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка при чтении данных", e)
            emptyList()
        }
    }
}

