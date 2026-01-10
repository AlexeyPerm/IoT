package com.example.iot

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * Главная активность приложения для мониторинга температуры печи.
 *
 * Предоставляет интерфейс для отображения текущей температуры, обновления данных
 * через pull-to-refresh и перехода к графику истории температуры.
 *
 * Поддерживает два режима работы:
 * - Режим разработки: использует симуляцию данных (по умолчанию)
 * - Режим продакшена: подключается к реальному серверу через API
 *
 * @author Ремянников Валентин Владимирович
 * @author Котов Алексей Валерьевич
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Тег для логирования */
        private const val TAG = "MainActivity"

        /** Имя файла для хранения данных о температуре */
        private const val TEMPERATURE_DATA_FILE = "temperature_data.json"

        /** Формат даты и времени для записи в файл */
        private const val DATE_FORMAT = "dd.MM.yyyy HH:mm"

        /** Задержка для симуляции сетевого запроса (мс) */
        private const val SIMULATION_DELAY_MS = 2000L

        /** Минимальная температура для симуляции (°C) */
        private const val MIN_TEMPERATURE = 200

        /** Максимальная температура для симуляции (°C) */
        private const val MAX_TEMPERATURE = 1500

        /** Базовый URL сервера (для режима продакшена) */
        private const val BASE_URL_EMULATOR = "http://10.0.2.2:8080/"  // Для Android эмулятора
        private const val BASE_URL_LOCALHOST = "http://localhost:8080/"  // Для реального устройства

        /** Сообщения для пользователя */
        private const val MSG_LOADING = "Загрузка..."
        private const val MSG_ERROR_CONNECTION = "Ошибка соединения"
        private const val MSG_NO_DATA = "N/A"
    }

    // UI элементы
    private lateinit var tvTemperatureValue: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var lottieAnimationView: LottieAnimationView

    // Gson экземпляр для работы с JSON
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Handler для работы с UI из фоновых потоков
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initializeViews()
            setupSwipeRefresh()
            setupHistoryButton()
            initializeTemperatureDataFile()

            // Загружаем температуру при запуске
            fetchCurrentTemperature()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при инициализации активности", e)
            showError("Ошибка инициализации приложения")
        }
    }

    /**
     * Инициализирует все UI элементы активности.
     */
    private fun initializeViews() {
        tvTemperatureValue = findViewById(R.id.tvTemperatureValue)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        lottieAnimationView = findViewById(R.id.lottieAnimationView)
    }

    /**
     * Настраивает SwipeRefreshLayout для обновления данных при свайпе вниз.
     */
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeColors(
            Color.BLACK,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW
        )
        swipeRefreshLayout.setProgressViewOffset(false, 1, 200)
        swipeRefreshLayout.setOnRefreshListener {
            fetchCurrentTemperature()
        }
    }

    /**
     * Настраивает кнопку истории для перехода к графику температуры.
     */
    private fun setupHistoryButton() {
        val btnHistory = findViewById<ImageView>(R.id.btnHistory)
        btnHistory.setOnClickListener {
            try {
                openTemperatureHistory()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при открытии истории температуры", e)
                showError("Не удалось открыть историю")
            }
        }
    }

    /**
     * Открывает активность с графиком истории температуры.
     */
    private fun openTemperatureHistory() {
        val temperatureData = TemperatureDataRepository.readTemperatureDataFromFile(
            this,
            R.raw.temperature_data
        )
        val intent = Intent(this, TemperatureGraphActivity::class.java).apply {
            putParcelableArrayListExtra("temperatureData", ArrayList(temperatureData))
        }
        startActivity(intent)
    }

    /**
     * Загружает текущую температуру с сервера или использует симуляцию.
     *
     * По умолчанию использует симуляцию данных. Для работы с реальным сервером
     * необходимо раскомментировать вызов fetchRealTemperature() и закомментировать
     * вызов simulateNetworkRequest().
     */
    private fun fetchCurrentTemperature() {
        showLoading()

        // РЕЖИМ РАЗРАБОТКИ: используем симуляцию (по умолчанию)
        simulateNetworkRequest()

        // РЕЖИМ ПРОДАКШЕНА: для работы с реальным сервером раскомментируйте следующую строку
        // и закомментируйте simulateNetworkRequest()
        // fetchRealTemperature()
    }

    /**
     * Симулирует сетевой запрос для разработки и тестирования.
     *
     * Генерирует случайное значение температуры в заданном диапазоне и сохраняет его в файл.
     * После задержки обновляет UI с полученным значением.
     */
    private fun simulateNetworkRequest() {
        swipeRefreshLayout.isRefreshing = false

        mainHandler.postDelayed({
            try {
                val randomTemperature = Random.nextInt(MIN_TEMPERATURE, MAX_TEMPERATURE)
                Log.d(TAG, "[MOCK] Сгенерирована температура: ${randomTemperature}°C")

                saveTemperatureToFile(randomTemperature)
                updateTemperatureDisplay(randomTemperature)
                hideLoading()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при симуляции запроса", e)
                showError("Ошибка загрузки данных")
                hideLoading()
            }
        }, SIMULATION_DELAY_MS)
    }

    /**
     * Выполняет реальный сетевой запрос к серверу для получения текущей температуры.
     *
     * Использует Retrofit для HTTP-запроса к API сервера.
     * URL сервера настраивается через константы BASE_URL_EMULATOR или BASE_URL_LOCALHOST.
     *
     * Note:
     * - Для Android эмулятора используйте BASE_URL_EMULATOR (10.0.2.2)
     * - Для реального устройства используйте IP адрес компьютера в локальной сети
     */
    private fun fetchRealTemperature() {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL_EMULATOR)  // Измените на IP вашего сервера для реального устройства
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val temperatureApi = retrofit.create(TemperatureApi::class.java)

            temperatureApi.getCurrentTemperature().enqueue(object : Callback<TemperatureResponse> {
                override fun onResponse(
                    call: Call<TemperatureResponse>,
                    response: Response<TemperatureResponse>
                ) {
                    if (response.isSuccessful) {
                        val temperature = response.body()?.temperature
                        if (temperature != null && temperature > 0) {
                            val temperatureInt = temperature.toInt()
                            saveTemperatureToFile(temperatureInt)
                            updateTemperatureDisplay(temperatureInt)
                            Log.d(TAG, "Температура успешно получена: ${temperatureInt}°C")
                        } else {
                            showError(MSG_NO_DATA)
                            Log.w(TAG, "Сервер вернул некорректные данные")
                        }
                    } else {
                        showError(MSG_ERROR_CONNECTION)
                        Log.w(TAG, "Ошибка HTTP: ${response.code()}")
                    }
                    hideLoading()
                    swipeRefreshLayout.isRefreshing = false
                }

                override fun onFailure(call: Call<TemperatureResponse>, t: Throwable) {
                    showError(MSG_ERROR_CONNECTION)
                    Log.e(TAG, "Ошибка соединения с сервером", t)
                    hideLoading()
                    swipeRefreshLayout.isRefreshing = false
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Критическая ошибка при выполнении запроса", e)
            showError("Ошибка при выполнении запроса")
            hideLoading()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    /**
     * Обновляет отображение температуры в UI.
     *
     * @param temperature Температура в градусах Цельсия
     */
    private fun updateTemperatureDisplay(temperature: Int) {
        tvTemperatureValue.text = "${temperature}°C"
    }

    /**
     * Показывает индикатор загрузки.
     */
    private fun showLoading() {
        tvTemperatureValue.text = MSG_LOADING
        progressBar.visibility = ProgressBar.VISIBLE
    }

    /**
     * Скрывает индикатор загрузки.
     */
    private fun hideLoading() {
        progressBar.visibility = ProgressBar.GONE
    }

    /**
     * Показывает сообщение об ошибке пользователю.
     *
     * @param message Текст сообщения об ошибке
     */
    private fun showError(message: String) {
        tvTemperatureValue.text = message
    }

    /**
     * Сохраняет значение температуры в файл данных.
     *
     * Данные сохраняются в формате JSON с временной меткой, округленной до часа.
     * Если запись для текущего часа уже существует, она обновляется.
     * Иначе создается новая запись.
     *
     * @param temperature Температура в градусах Цельсия
     */
    private fun saveTemperatureToFile(temperature: Int) {
        try {
            val file = getTemperatureDataFile()
            val type = object : TypeToken<MutableList<TemperatureData>>() {}.type

            // Получаем текущее время и округляем до часов
            val calendar = Calendar.getInstance().apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Форматируем дату в строку
            val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val formattedDate = dateFormat.format(calendar.time)

            // Читаем существующие данные из файла
            val temperatureDataList = if (file.exists() && file.length() > 0) {
                try {
                    gson.fromJson<MutableList<TemperatureData>>(file.bufferedReader(), type)
                        ?: mutableListOf()
                } catch (e: JsonSyntaxException) {
                    Log.w(TAG, "Ошибка парсинга JSON файла, создается новый", e)
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            // Проверяем, есть ли уже запись для этого часа
            val existingEntry = temperatureDataList.find { it.date == formattedDate }
            if (existingEntry != null) {
                // Обновляем существующую запись
                existingEntry.temperature = temperature
                Log.d(TAG, "Запись обновлена: $formattedDate, $temperature°C")
            } else {
                // Добавляем новую запись
                temperatureDataList.add(TemperatureData(formattedDate, temperature))
                Log.d(TAG, "Новая запись добавлена: $formattedDate, $temperature°C")
            }

            // Записываем обновленные данные в файл
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                gson.toJson(temperatureDataList, writer)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при сохранении данных в файл", e)
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка при сохранении данных", e)
        }
    }

    /**
     * Инициализирует файл данных о температуре при первом запуске приложения.
     *
     * Если файл не существует, копирует начальные данные из ресурсов (res/raw).
     * Это обеспечивает наличие базовых данных для отображения графика.
     */
    private fun initializeTemperatureDataFile() {
        try {
            val file = getTemperatureDataFile()
            if (!file.exists()) {
                // Копируем начальные данные из res/raw
                val inputStream = resources.openRawResource(R.raw.temperature_data)
                val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                file.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                }
                Log.d(TAG, "Файл данных успешно инициализирован из res/raw")
            } else {
                Log.d(TAG, "Файл данных уже существует")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Ошибка при инициализации файла данных", e)
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка при инициализации файла данных", e)
        }
    }

    /**
     * Получает файл для хранения данных о температуре.
     *
     * @return File объект, представляющий файл данных
     */
    private fun getTemperatureDataFile(): File {
        val directory = getExternalFilesDir(null)
        return File(directory, TEMPERATURE_DATA_FILE)
    }
}

