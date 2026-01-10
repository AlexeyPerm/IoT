package com.example.iot

import retrofit2.Call
import retrofit2.http.GET

/**
 * API интерфейс для работы с сервером температуры.
 *
 * Определяет эндпоинты для получения данных о температуре печи.
 * Используется Retrofit для выполнения HTTP-запросов.
 */
interface TemperatureApi {
    /**
     * Получает текущую температуру с сервера.
     *
     * @return Call объект с ответом, содержащим текущую температуру
     *
     * Эндпоинт: GET /current_temperature
     * Ответ: {"temperature": 450.75}
     */
    @GET("current_temperature")
    fun getCurrentTemperature(): Call<TemperatureResponse>
}

/**
 * Модель ответа сервера с текущей температурой.
 *
 * @property temperature Текущая температура в градусах Цельсия
 */
data class TemperatureResponse(
    val temperature: Float
)
