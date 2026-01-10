package com.example.iot

import android.os.Parcel
import android.os.Parcelable

/**
 * Модель данных о температуре с временной меткой.
 *
 * Используется для хранения и передачи данных о температуре печи
 * с указанием времени измерения. Реализует Parcelable для передачи
 * между активностями через Intent.
 *
 * @property date Дата и время измерения в формате "dd.MM.yyyy HH:mm"
 * @property temperature Температура в градусах Цельсия
 */
data class TemperatureData(
    val date: String,
    var temperature: Int
) : Parcelable {

    /**
     * Конструктор для создания объекта из Parcel.
     *
     * Используется при десериализации объекта, переданного через Intent.
     *
     * @param parcel Parcel объект с сериализованными данными
     */
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt()
    )

    /**
     * Записывает данные объекта в Parcel.
     *
     * @param parcel Parcel объект для записи данных
     * @param flags Флаги для записи (не используются)
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(date)
        parcel.writeInt(temperature)
    }

    /**
     * Описывает содержимое Parcelable объекта.
     *
     * @return 0, так как содержимое не содержит файловых дескрипторов
     */
    override fun describeContents(): Int {
        return 0
    }

    /**
     * Companion объект для создания экземпляров из Parcel.
     *
     * Реализует Parcelable.Creator для автоматической десериализации.
     */
    companion object CREATOR : Parcelable.Creator<TemperatureData> {
        /**
         * Создает объект TemperatureData из Parcel.
         *
         * @param parcel Parcel объект с данными
         * @return Экземпляр TemperatureData
         */
        override fun createFromParcel(parcel: Parcel): TemperatureData {
            return TemperatureData(parcel)
        }

        /**
         * Создает массив объектов TemperatureData.
         *
         * @param size Размер массива
         * @return Массив nullable объектов TemperatureData
         */
        override fun newArray(size: Int): Array<TemperatureData?> {
            return arrayOfNulls(size)
        }
    }
}
