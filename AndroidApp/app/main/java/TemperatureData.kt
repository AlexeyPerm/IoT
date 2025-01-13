package com.example.iot

import android.os.Parcel
import android.os.Parcelable

data class TemperatureData(
    val date: String, // Дата и время в формате "dd.MM.yyyy HH:mm"
    var temperature: Int // Температура
) : Parcelable {
    // Конструктор для создания объекта из Parcel
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "", // Чтение строки
        parcel.readInt() // Чтение целого числа
    )

    // Запись объекта в Parcel
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(date)
        parcel.writeInt(temperature)
    }

    // Описание содержимого
    override fun describeContents(): Int {
        return 0
    }

    // Объект CREATOR для создания экземпляров из Parcel
    companion object CREATOR : Parcelable.Creator<TemperatureData> {
        override fun createFromParcel(parcel: Parcel): TemperatureData {
            return TemperatureData(parcel)
        }

        override fun newArray(size: Int): Array<TemperatureData?> {
            return arrayOfNulls(size)
        }
    }
}
