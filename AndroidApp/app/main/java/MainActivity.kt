package com.example.iot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.random.Random
import android.content.Context
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var tvTemperatureValue: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var lottieAnimationView: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Установка XML-макета

        // Инициализация файла с данными при первом запуске
        initializeTemperatureDataFile(this)

        // Находим LottieAnimationView
        lottieAnimationView = findViewById(R.id.lottieAnimationView)


        // Находим TextView для отображения температуры
        tvTemperatureValue = findViewById(R.id.tvTemperatureValue)
        // Находим ProgressBar
        progressBar = findViewById(R.id.progressBar)
        // Находим SwipeRefreshLayout
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        // Настройка SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeResources(android.R.color.black) // Цвет индикатора
        swipeRefreshLayout.setProgressViewOffset(false, 1, 200) // Настройка отступов
        swipeRefreshLayout.setColorSchemeColors(
            Color.BLACK,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW
        )

        swipeRefreshLayout.setOnRefreshListener {
            // Запускаем запрос при обновлении
            fetchCurrentTemperature()
           // fetchRealTemperature()
        }


        // Находим кнопку в макете
        val btnHistory = findViewById<ImageView>(R.id.btnHistory)

        // Устанавливаем обработчик нажатия на кнопку
        btnHistory.setOnClickListener {
            val temperatureData = TemperatureDataRepository.readTemperatureDataFromFile(
                this,
                R.raw.temperature_data
            )
            val intent = Intent(this, TemperatureGraphActivity::class.java).apply {
                putParcelableArrayListExtra("temperatureData", ArrayList(temperatureData))
            }
            startActivity(intent)
        }

        // Запускаем запрос
        fetchCurrentTemperature()
    }

    private fun fetchCurrentTemperature() {
        // Показываем ProgressBar
        progressBar.visibility = ProgressBar.VISIBLE
        //tvTemperatureValue.text = "Загрузка..."

        // Имитация запроса (возвращает случайное число через 3 секунды)
        simulateNetworkRequest()

        //fetchRealTemperature()
    }

    private fun simulateNetworkRequest() {
        tvTemperatureValue.text = ""
        swipeRefreshLayout.isRefreshing = false
        // Используем Handler для задержки
        Handler(Looper.getMainLooper()).postDelayed({
            // Генерируем случайное число от 200 до 1500
            val randomTemperature = Random.nextInt(200, 1500)
            
            saveTemperatureToFile(applicationContext, randomTemperature)
            // Обновляем UI
            tvTemperatureValue.text = "$randomTemperature°C"
            // Скрываем ProgressBar
            progressBar.visibility = ProgressBar.GONE

        }, 2000) // Задержка 3 секунды
    }

    private fun fetchRealTemperature() {
        // Создаем Retrofit-клиент
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:8080/")  // URL сервера
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Создаем API-интерфейс
        val temperatureApi = retrofit.create(TemperatureApi::class.java)

        // Отправляем запрос
        temperatureApi.getCurrentTemperature().enqueue(object : Callback<TemperatureResponse> {
            override fun onResponse(
                call: Call<TemperatureResponse>,
                response: Response<TemperatureResponse>
            ) {
                if (response.isSuccessful) {
                    val temperature = response.body()?.temperature
                    if (temperature != null) {
                        // Обновляем UI с полученной температурой
                        tvTemperatureValue.text = "${temperature}°C"

                        saveTemperatureToFile(applicationContext, temperature.toInt())
                    } else {
                        tvTemperatureValue.text = "N/A"
                    }
                } else {
                    tvTemperatureValue.text = "Ошибка соединения"
                }
                // Скрываем ProgressBar после завершения запроса
                progressBar.visibility = ProgressBar.GONE
            }

            override fun onFailure(call: Call<TemperatureResponse>, t: Throwable) {
                tvTemperatureValue.text = "Ошибка соединения"
                // Скрываем ProgressBar после завершения запроса
                progressBar.visibility = ProgressBar.GONE
            }
        })
        swipeRefreshLayout.isRefreshing = false
    }

}
private fun saveTemperatureToFile(context: Context, temperature: Int) {
    val file = File(context.getExternalFilesDir(null), "temperature_data.json")
    val gson = Gson()
    val type = object : TypeToken<MutableList<TemperatureData>>() {}.type

    // Получаем текущее время и округляем до часов
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // Форматируем дату в строку
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(calendar.time)

    // Чтение существующих данных из файла
    val temperatureDataList: MutableList<TemperatureData> = if (file.exists()) {
        gson.fromJson(file.bufferedReader(), type)
    } else {
        mutableListOf()
    }

    // Проверяем, есть ли уже запись для этого часа
    val existingEntry = temperatureDataList.find { it.date == formattedDate }
    if (existingEntry != null) {
        // Если запись есть, обновляем температуру
        existingEntry.temperature = temperature
        println("Запись обновлена: $formattedDate, $temperature")
    } else {
        // Если записи нет, добавляем новую
        temperatureDataList.add(TemperatureData(formattedDate, temperature))
        println("Новая запись добавлена: $formattedDate, $temperature")
    }

    // Записываем обновленные данные в файл
    file.bufferedWriter().use { writer ->
        gson.toJson(temperatureDataList, writer)
    }

    // Логируем содержимое файла для проверки
    val fileContent = file.readText()
    println("Содержимое файла:\n$fileContent")
}

private fun initializeTemperatureDataFile(context: Context) {
    val file = File(context.getExternalFilesDir(null), "temperature_data.json")
    if (!file.exists()) {
        // Если файл не существует, копируем данные из res/raw
        val inputStream = context.resources.openRawResource(R.raw.temperature_data)
        val jsonString = inputStream.bufferedReader().use { it.readText() }

        // Записываем данные в новый файл
        file.bufferedWriter().use { writer ->
            writer.write(jsonString)
        }
        println("Файл успешно скопирован из res/raw.")
    } else {
        println("Файл уже существует, копирование не требуется.")
    }
}

