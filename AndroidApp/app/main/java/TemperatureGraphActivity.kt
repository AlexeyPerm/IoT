package com.example.iot

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.example.iot.ui.theme.IoTTheme

/**
 * Активность для отображения графика истории температуры.
 *
 * Отображает интерактивный график температуры с поддержкой масштабирования и прокрутки.
 * Поддерживает полноэкранный режим с скрытым статус-баром для лучшего просмотра графика.
 *
 * Получает данные о температуре через Intent в виде ParcelableArrayListExtra
 * с ключом "temperatureData".
 *
 * @author Ремянников Валентин Владимирович
 * @author Котов Алексей Валерьевич
 */
class TemperatureGraphActivity : ComponentActivity() {

    companion object {
        /** Тег для логирования */
        private const val TAG = "TemperatureGraphActivity"

        /** Ключ для передачи данных через Intent */
        const val INTENT_KEY_TEMPERATURE_DATA = "temperatureData"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Получение данных из Intent
            val temperatureData = intent.getParcelableArrayListExtra<TemperatureData>(
                INTENT_KEY_TEMPERATURE_DATA
            ) ?: emptyList()

            if (temperatureData.isEmpty()) {
                Log.w(TAG, "Получен пустой список данных о температуре")
            } else {
                Log.d(TAG, "Получено записей для отображения на графике: ${temperatureData.size}")
            }

            // Настройка UI с Jetpack Compose
            setContent {
                IoTTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TemperatureGraph(
                            modifier = Modifier.fillMaxSize(),
                            temperatureData = temperatureData
                        )
                    }
                }
            }

            // Настройка скрытия статус-бара в полноэкранном режиме
            setupFullscreenMode()

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при создании активности графика температуры", e)
            finish() // Закрываем активность при ошибке
        }
    }

    /**
     * Настраивает полноэкранный режим с скрытым статус-баром.
     *
     * Использует разные API в зависимости от версии Android:
     * - Android 11+ (API 30+): WindowInsetsController
     * - Более ранние версии: systemUiVisibility
     */
    private fun setupFullscreenMode() {
        lifecycle.addObserver(object : LifecycleObserver {
            @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
            fun onResume() {
                hideStatusBar()
            }
        })
    }

    /**
     * Скрывает статус-бар для полноэкранного режима.
     *
     * Поддерживает разные версии Android API для совместимости.
     */
    private fun hideStatusBar() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window.insetsController?.hide(WindowInsets.Type.statusBars())
            } else {
                // Более ранние версии Android
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось скрыть статус-бар", e)
        }
    }
}
