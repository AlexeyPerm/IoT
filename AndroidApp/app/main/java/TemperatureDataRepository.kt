import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.iot.TemperatureData
import java.io.File

object TemperatureDataRepository {

    // Чтение данных из JSON-файла в res/raw
    fun readTemperatureDataFromFile(context: Context, temperatureData: Int): List<TemperatureData> {
        val file = File(context.getExternalFilesDir(null), "temperature_data.json")
        val gson = Gson()
        val type = object : TypeToken<List<TemperatureData>>() {}.type

        return if (file.exists()) {
            gson.fromJson(file.bufferedReader(), type)
        } else {
            emptyList()
        }
    }
}

