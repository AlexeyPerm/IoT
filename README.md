# Авторы:
  РЕМЯННИКОВ ВАЛЕНТИН ВЛАДИМИРОВИЧ
  
  КОТОВ АЛЕКСЕЙ ВАЛЕРЬЕВИЧ
# Приложение Android для мониторинга температуры
 # Оглавление

- [Описание проекта](#Описание-проекта)
  - [1. Для чего](#1-для-чего)
  - [2. Пример работы](#2-примеры-работы)
- [Технологии](#Технологии)

# Описание проекта
### 1. Для чего: 

### 2. Примеры работы:
После установки температура в 444 градуса приходит уведомление на почту о включении реле. Так же приходит уведомление
на почту, информирующее о слишком низкой текущей температура по сравнению с заданной.
![img.png](img.png)

![img_1.png](img_1.png)
# Технологии
- **Язык программирования**: Kotlin

### Основные библиотеки:
- **[Lottie](https://airbnb.io/lottie/)** (`com.airbnb.android:lottie:6.1.0`) — для отображения анимаций в формате JSON.
- **[Retrofit](https://square.github.io/retrofit/)** (`com.squareup.retrofit2:retrofit:2.9.0`) — для выполнения сетевых запросов.
- **[Retrofit Gson Converter](https://square.github.io/retrofit/)** (`com.squareup.retrofit2:converter-gson:2.9.0`) — для преобразования JSON-ответов в объекты Kotlin/Java.
- **[OkHttp](https://square.github.io/okhttp/)** (`com.squareup.okhttp3:okhttp:4.9.3`) — HTTP-клиент для работы с сетевыми запросами.
- **[OkHttp Logging Interceptor](https://square.github.io/okhttp/)** (`com.squareup.okhttp3:logging-interceptor:4.9.3`) — для логирования сетевых запросов и ответов.
- **[Gson](https://github.com/google/gson)** (`com.google.code.gson:gson:2.10.1`) — для сериализации и десериализации JSON.

### UI
- **[Jetpack Compose Material 3](https://developer.android.com/jetpack/compose)** (`androidx.compose.material3:material3:1.1.1`) — для создания UI с использованием Material Design 3.
- **[Material Components](https://material.io/develop/android)** (`com.google.android.material:material:1.9.0`) — для использования Material Design компонентов.

### Графики
- **[Canvas](https://developer.android.com/jetpack/compose/graphics)** — для кастомной отрисовки графиков и элементов интерфейса. Используется для создания интерактивного графика температуры с поддержкой масштабирования и жестов.
