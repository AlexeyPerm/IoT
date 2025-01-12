package com.example.iot

import retrofit2.Call
import retrofit2.http.GET

interface TemperatureApi {
    @GET("current_temperature")  // путь к  API
    fun getCurrentTemperature(): Call<TemperatureResponse>
}

data class TemperatureResponse(
    val temperature: Float
)
