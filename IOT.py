"""
IoT сервер для управления температурой печи.

Сервер предоставляет REST API для мониторинга температуры и управления реле нагрева.
Поддерживает два режима работы: режим разработки с моками (по умолчанию) и режим
продакшена с реальным оборудованием на Raspberry Pi.

РЕЖИМЫ РАБОТЫ:
    - РЕЖИМ РАЗРАБОТКИ (по умолчанию): Используются моки для имитации данных.
      Работает на любом компьютере без дополнительного оборудования.
    - РЕЖИМ ПРОДАКШЕНА: Для работы с реальным Raspberry Pi нужно раскомментировать
      код, помеченный как "#prod work with ioT"

Для переключения в продакшен режим:
    1. Раскомментируйте импорт gpiozero
    2. Раскомментируйте инициализацию GPIO устройств
    3. Раскомментируйте реальный код в функциях read_temperature() и control_heating_relay()
    4. Установите зависимость: pip install gpiozero

API Эндпоинты:
    - GET  /                       - Веб-интерфейс для управления
    - GET  /current_temperature    - Получить текущую температуру (JSON)
    - POST /setpoint               - Установить уставку температуры

Авторы:
    Ремянников Валентин Владимирович
    Котов Алексей Валерьевич
"""

import json
import logging
import smtplib
import random
from email.mime.text import MIMEText
from typing import Optional, Dict, Any

from bottle import template, request, route, run, response

# ============================================
# КОНСТАНТЫ
# ============================================

# Параметры сервера
SERVER_HOST = '0.0.0.0'
SERVER_PORT = 8080
SERVER_DEBUG = True

# Параметры оборудования
RELAY_PIN = 17  # GPIO пин, к которому подключено реле
ADC_CHANNEL = 0  # Канал АЦП для термосопротивления

# Параметры температуры
MIN_TEMPERATURE = 200.0  # Минимальная температура (°C)
MAX_TEMPERATURE = 1500.0  # Максимальная температура (°C)
TEMPERATURE_THRESHOLD = 10.0  # Порог для уведомления о низкой температуре (°C)
MOCK_TEMPERATURE_MIN = 200  # Минимальная температура для мока (°C)
MOCK_TEMPERATURE_MAX = 1500  # Максимальная температура для мока (°C)

# Параметры АЦП (для реального оборудования)
ADC_VOLTAGE_MAX = 3.3  # Максимальное напряжение АЦП (В)
RESISTOR_LOW = 39.225  # Сопротивление нижнего резистора делителя (Ом)
RESISTOR_HIGH = 92.775  # Сопротивление верхнего резистора делителя (Ом)
RESISTOR_DIVIDER = 50.0  # Сопротивление делителя (Оm)
TEMP_RANGE_MIN = -50.0  # Минимальная температура для преобразования (°C)
TEMP_RANGE_MAX = 500.0  # Максимальная температура для преобразования (°C)

# Параметры email-уведомлений
SMTP_SERVER = 'smtp.gmail.com'
SMTP_PORT = 587
CREDENTIALS_FILE = 'cred.json'

# ============================================
# PROD: Раскомментировать для работы с реальным IoT на Raspberry Pi
# ============================================
# from gpiozero import MCP3008, OutputDevice

# ============================================
# ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ
# ============================================

# Текущая уставка температуры
setpoint: float = 25.0

# GPIO устройства (None в режиме разработки)
relay = None  # Мок для реле
adc = None    # Мок для АЦП

# Флаг доступности оборудования
HARDWARE_AVAILABLE = False

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ============================================
# PROD: Раскомментировать для работы с реальным IoT на Raspberry Pi
# ============================================
# Инициализация GPIO устройств (работает только на Raspberry Pi)
# try:
#     relay = OutputDevice(RELAY_PIN)
#     adc = MCP3008(ADC_CHANNEL)
#     HARDWARE_AVAILABLE = True
#     logger.info(f"GPIO устройства инициализированы: реле на пине {RELAY_PIN}, АЦП на канале {ADC_CHANNEL}")
# except Exception as e:
#     logger.warning(f"GPIO устройства недоступны: {e}")
#     relay = None
#     adc = None
#     HARDWARE_AVAILABLE = False


def load_email_credentials() -> Optional[Dict[str, str]]:
    """
    Загружает учетные данные для email из файла cred.json.

    Returns:
        Optional[Dict[str, str]]: Словарь с ключами 'SRC_EMAIL_ADDRESS', 'EMAIL_PASSWORD',
                                  'DST_EMAIL_ADDRESS' или None, если файл не найден или поврежден.

    Note:
        Файл должен содержать JSON с полями:
        {
            "SRC_EMAIL_ADDRESS": "sender@gmail.com",
            "EMAIL_PASSWORD": "app-password",
            "DST_EMAIL_ADDRESS": "recipient@gmail.com"
        }
    """
    try:
        with open(CREDENTIALS_FILE, 'r', encoding='utf-8') as cred_file:
            credentials = json.load(cred_file)
            required_keys = ["SRC_EMAIL_ADDRESS", "EMAIL_PASSWORD", "DST_EMAIL_ADDRESS"]
            if not all(key in credentials for key in required_keys):
                logger.error(f"Файл {CREDENTIALS_FILE} не содержит все необходимые ключи: {required_keys}")
                return None
            return credentials
    except FileNotFoundError:
        logger.debug(f"Файл {CREDENTIALS_FILE} не найден. Email-уведомления отключены.")
        return None
    except json.JSONDecodeError as e:
        logger.error(f"Ошибка парсинга {CREDENTIALS_FILE}: {e}")
        return None
    except Exception as e:
        logger.error(f"Неожиданная ошибка при чтении {CREDENTIALS_FILE}: {e}")
        return None


def send_email_notification(subject: str, message: str) -> bool:
    """
    Отправляет email-уведомление через SMTP сервер Gmail.

    Args:
        subject: Тема письма
        message: Текст письма

    Returns:
        bool: True, если письмо успешно отправлено, False в противном случае.

    Note:
        Для работы требуется файл cred.json с учетными данными.
        Для Gmail необходимо использовать пароль приложения, а не обычный пароль аккаунта.
        Если файл cred.json отсутствует, функция молча завершается без отправки письма.
    """
    credentials = load_email_credentials()
    if not credentials:
        logger.info(f"Email-уведомление '{subject}' не отправлено: файл {CREDENTIALS_FILE} не настроен.")
        return False

    try:
        src_email_address = credentials["SRC_EMAIL_ADDRESS"]
        email_password = credentials["EMAIL_PASSWORD"]
        dst_email_address = credentials["DST_EMAIL_ADDRESS"]

        msg = MIMEText(message, 'plain', 'utf-8')
        msg['Subject'] = subject
        msg['From'] = src_email_address
        msg['To'] = dst_email_address

        # Подключение к SMTP-серверу Gmail
        with smtplib.SMTP(SMTP_SERVER, SMTP_PORT) as server:
            server.starttls()  # Включение шифрования TLS
            server.login(src_email_address, email_password)
            server.send_message(msg)

        logger.info(f"Email-уведомление успешно отправлено: '{subject}' -> {dst_email_address}")
        return True
    except smtplib.SMTPAuthenticationError as e:
        logger.error(f"Ошибка аутентификации SMTP: {e}. Проверьте учетные данные в {CREDENTIALS_FILE}")
        return False
    except smtplib.SMTPException as e:
        logger.error(f"Ошибка SMTP при отправке письма: {e}")
        return False
    except Exception as e:
        logger.error(f"Неожиданная ошибка при отправке письма: {e}", exc_info=True)
        return False


def read_temperature() -> Optional[float]:
    """
    Читает текущую температуру с датчика.

    В режиме разработки (HARDWARE_AVAILABLE = False) возвращает случайное значение
    в диапазоне от MOCK_TEMPERATURE_MIN до MOCK_TEMPERATURE_MAX.

    В режиме продакшена читает значение с АЦП MCP3008 и преобразует его в температуру
    по формуле для термосопротивления ТСП 100.

    Returns:
        Optional[float]: Температура в градусах Цельсия с точностью до 2 знаков после запятой,
                        или None в случае ошибки.

    Note:
        Для работы в продакшен режиме необходимо раскомментировать код, помеченный как
        "#prod work with ioT".
    """
    # ============================================
    # MOCK: Имитация температуры для разработки (активно по умолчанию)
    # ============================================
    if not HARDWARE_AVAILABLE:
        temperature = round(random.uniform(MOCK_TEMPERATURE_MIN, MOCK_TEMPERATURE_MAX), 2)
        logger.debug(f"[MOCK] Сгенерирована температура: {temperature}°C")
        return temperature

    # ============================================
    # PROD: Раскомментировать для работы с реальным IoT на Raspberry Pi
    # ============================================
    # if adc is None:
    #     logger.error("АЦП не инициализирован")
    #     return None
    #
    # try:
    #     # Чтение значения с АЦП MCP3008 (0.0 - 1.0)
    #     adc_value = adc.value
    #     if adc_value is None:
    #         logger.error("Не удалось прочитать значение с АЦП")
    #         return None
    #
    #     # Преобразование значения АЦП в напряжение (0.0 - 3.3 В)
    #     voltage = adc_value * ADC_VOLTAGE_MAX
    #
    #     # Расчет опорных напряжений делителя напряжения
    #     # Используется термосопротивление ТСП 100 с делителем напряжения
    #     low_voltage = (ADC_VOLTAGE_MAX * RESISTOR_LOW) / (RESISTOR_DIVIDER + RESISTOR_LOW)
    #     high_voltage = (ADC_VOLTAGE_MAX * RESISTOR_HIGH) / (RESISTOR_DIVIDER + RESISTOR_HIGH)
    #
    #     # Преобразование напряжения в температуру по квадратичной формуле
    #     voltage_range = high_voltage - low_voltage
    #     normalized_voltage = (voltage - low_voltage) / voltage_range if voltage_range != 0 else 0
    #     temperature_range = TEMP_RANGE_MAX - TEMP_RANGE_MIN
    #     temperature_celsius = (normalized_voltage ** 2) * temperature_range + TEMP_RANGE_MIN
    #
    #     # Ограничение температуры допустимым диапазоном
    #     temperature_celsius = max(TEMP_RANGE_MIN, min(TEMP_RANGE_MAX, temperature_celsius))
    #
    #     result = round(temperature_celsius, 2)
    #     logger.debug(f"Температура считана с датчика: {result}°C")
    #     return result
    #
    # except Exception as e:
    #     logger.error(f"Ошибка при чтении температуры: {e}", exc_info=True)
    #     return None

    logger.warning("Функция read_temperature вызвана, но код для продакшена не активирован")
    return None


def validate_temperature(temperature: float) -> bool:
    """
    Проверяет, находится ли температура в допустимом диапазоне.

    Args:
        temperature: Температура для проверки (°C)

    Returns:
        bool: True, если температура в допустимом диапазоне, False в противном случае
    """
    return MIN_TEMPERATURE <= temperature <= MAX_TEMPERATURE


def control_heating_relay(target_temperature: float) -> None:
    """
    Управляет реле нагрева в зависимости от текущей температуры.

    Включает реле, если текущая температура ниже целевой уставки, и выключает в противном случае.
    Отправляет email-уведомления о критических состояниях (низкая температура).

    Args:
        target_temperature: Целевая температура (уставка) в градусах Цельсия

    Note:
        В режиме разработки только логирует действия, не управляя реальным оборудованием.
        В режиме продакшена управляет GPIO реле и отправляет email-уведомления.

        Для работы в продакшен режиме необходимо раскомментировать код, помеченный как
        "#prod work with ioT".
    """
    if not validate_temperature(target_temperature):
        logger.warning(f"Некорректная целевая температура: {target_temperature}°C. "
                      f"Диапазон: {MIN_TEMPERATURE} - {MAX_TEMPERATURE}°C")
        return

    current_temperature = read_temperature()

    if current_temperature is None:
        logger.error("Не удалось прочитать температуру. Управление реле невозможно.")
        return

    # ============================================
    # MOCK: Логирование для разработки (активно по умолчанию)
    # ============================================
    if not HARDWARE_AVAILABLE:
        temperature_diff = target_temperature - current_temperature
        if current_temperature < target_temperature:
            logger.info(f"[MOCK] Реле ВКЛЮЧЕНО: Температура {current_temperature}°C < Уставка {target_temperature}°C")
            if current_temperature <= target_temperature - TEMPERATURE_THRESHOLD:
                logger.warning(f"[MOCK] ВНИМАНИЕ: Низкая температура: {current_temperature}°C "
                             f"(ниже уставки на {temperature_diff:.1f}°C)")
        else:
            logger.info(f"[MOCK] Реле ОТКЛЮЧЕНО: Температура {current_temperature}°C >= Уставка {target_temperature}°C")
        return

    # ============================================
    # PROD: Раскомментировать для работы с реальным IoT на Raspberry Pi
    # ============================================
    # if relay is None:
    #     logger.error("Реле не инициализировано")
    #     return
    #
    # try:
    #     if current_temperature < target_temperature:
    #         # Включаем нагрев
    #         relay.on()
    #         logger.info(f"Реле ВКЛЮЧЕНО: Температура {current_temperature}°C < Уставка {target_temperature}°C")
    #         send_email_notification("Реле включено", f"Температура: {current_temperature}°C, Уставка: {target_temperature}°C")
    #
    #         # Проверка на критически низкую температуру
    #         if current_temperature <= target_temperature - TEMPERATURE_THRESHOLD:
    #             warning_message = (f"КРИТИЧЕСКИ НИЗКАЯ ТЕМПЕРАТУРА!\n"
    #                              f"Текущая: {current_temperature}°C\n"
    #                              f"Уставка: {target_temperature}°C\n"
    #                              f"Разница: {target_temperature - current_temperature:.1f}°C")
    #             logger.warning(f"[КРИТИЧНО] {warning_message}")
    #             send_email_notification("Внимание: низкая температура", warning_message)
    #     else:
    #         # Выключаем нагрев
    #         relay.off()
    #         logger.info(f"Реле ОТКЛЮЧЕНО: Температура {current_temperature}°C >= Уставка {target_temperature}°C")
    #         send_email_notification("Реле отключено", f"Температура достигла уставки: {current_temperature}°C")
    #
    # except Exception as e:
    #     logger.error(f"Ошибка при управлении реле: {e}", exc_info=True)


@route('/')
def index():
    """
    Главная страница с веб-интерфейсом для управления температурой.

    Returns:
        str: HTML-страница с текущей температурой и формой для установки уставки

    Note:
        Использует шаблон index.html из текущей директории.
    """
    try:
        temperature = read_temperature()
        return template("index.html", temperature=temperature, setpoint=setpoint)
    except Exception as e:
        logger.error(f"Ошибка при отображении главной страницы: {e}", exc_info=True)
        response.status = 500
        return f"<h1>Ошибка сервера</h1><p>{str(e)}</p>"


@route("/setpoint", method="POST")
def set_setpoint():
    """
    Устанавливает новую уставку температуры и управляет реле.

    Ожидает POST-запрос с полем 'setpoint' в форме.
    Валидирует значение на соответствие допустимому диапазону.

    Returns:
        str: HTML-страница с обновленными данными или страница с ошибкой

    Note:
        Ограничения: setpoint должен быть числом в диапазоне от 200 до 1500°C.
        Ограничения задаются в index.html и проверяются на сервере.
    """
    global setpoint  # Объявление глобальной переменной должно быть в начале функции
    
    try:
        new_setpoint_str = request.forms.get("setpoint")
        if new_setpoint_str is None:
            logger.warning("Получен запрос на установку уставки без значения")
            response.status = 400
            return template("index.html", temperature=read_temperature(), setpoint=setpoint,
                          error="Не указано значение уставки")

        try:
            new_setpoint = float(new_setpoint_str)
        except ValueError:
            logger.warning(f"Некорректное значение уставки: '{new_setpoint_str}'")
            response.status = 400
            return template("index.html", temperature=read_temperature(), setpoint=setpoint,
                          error=f"Некорректное значение: '{new_setpoint_str}'. Ожидается число.")

        # Валидация диапазона
        if not validate_temperature(new_setpoint):
            logger.warning(f"Уставка вне допустимого диапазона: {new_setpoint}°C")
            response.status = 400
            return template("index.html", temperature=read_temperature(), setpoint=setpoint,
                          error=f"Уставка {new_setpoint}°C вне допустимого диапазона "
                                f"({MIN_TEMPERATURE} - {MAX_TEMPERATURE}°C)")

        # Обновление глобальной переменной уставки
        old_setpoint = setpoint
        setpoint = new_setpoint

        logger.info(f"Уставка температуры изменена: {old_setpoint}°C -> {setpoint}°C")

        # Управление реле с новой уставкой
        control_heating_relay(setpoint)

        # Возврат обновленной страницы
        current_temperature = read_temperature()
        return template("index.html", temperature=current_temperature, setpoint=setpoint)

    except Exception as e:
        logger.error(f"Ошибка при установке уставки: {e}", exc_info=True)
        response.status = 500
        return template("index.html", temperature=read_temperature(), setpoint=setpoint,
                      error=f"Внутренняя ошибка сервера: {str(e)}")


@route('/current_temperature')
def get_current_temperature():
    """
    REST API эндпоинт для получения текущей температуры.

    Returns:
        str: JSON-ответ с текущей температурой или сообщением об ошибке

    Пример успешного ответа:
        {"temperature": 450.75}

    Пример ответа с ошибкой:
        {"error": "Не удалось получить температуру"}

    HTTP Status Codes:
        200: Успешное получение температуры
        500: Ошибка при чтении температуры
    """
    try:
        temperature = read_temperature()
        if temperature is not None:
            response.content_type = 'application/json; charset=utf-8'
            return json.dumps({"temperature": temperature}, ensure_ascii=False)
        else:
            logger.error("Не удалось получить температуру с датчика")
            response.status = 500
            response.content_type = 'application/json; charset=utf-8'
            return json.dumps({"error": "Не удалось получить температуру"}, ensure_ascii=False)
    except Exception as e:
        logger.error(f"Неожиданная ошибка при получении температуры: {e}", exc_info=True)
        response.status = 500
        response.content_type = 'application/json; charset=utf-8'
        return json.dumps({"error": f"Внутренняя ошибка сервера: {str(e)}"}, ensure_ascii=False)


def print_startup_banner():
    """
    Выводит информационный баннер при запуске сервера.
    """
    banner_width = 70
    print("=" * banner_width)
    print(" " * 15 + "IoT СЕРВЕР УПРАВЛЕНИЯ ТЕМПЕРАТУРОЙ ПЕЧИ")
    print("=" * banner_width)
    print(f"Сервер запущен на http://{SERVER_HOST}:{SERVER_PORT}")
    print(f"Веб-интерфейс: http://localhost:{SERVER_PORT}/")
    print(f"API эндпоинт: http://localhost:{SERVER_PORT}/current_temperature")
    print("-" * banner_width)

    if not HARDWARE_AVAILABLE:
        print("⚠️  РЕЖИМ РАЗРАБОТКИ: Используются моки (имитация данных)")
        print("   Для работы с реальным IoT на Raspberry Pi:")
        print("   1. Раскомментируйте код, помеченный как '#prod work with ioT'")
        print("   2. Установите: pip install gpiozero")
        print("   3. Перезапустите сервер")
    else:
        print("✅ РЕЖИМ ПРОДАКШЕНА: Используется реальное оборудование")
        print(f"   Реле: GPIO пин {RELAY_PIN}")
        print(f"   АЦП: канал {ADC_CHANNEL}")

    print("-" * banner_width)
    print(f"Диапазон температуры: {MIN_TEMPERATURE} - {MAX_TEMPERATURE}°C")
    print(f"Текущая уставка: {setpoint}°C")
    print("=" * banner_width)


if __name__ == "__main__":
    """
    Точка входа в приложение.

    Инициализирует и запускает веб-сервер Bottle для управления температурой печи.
    """
    print_startup_banner()

    try:
        run(host=SERVER_HOST, port=SERVER_PORT, debug=SERVER_DEBUG)
    except KeyboardInterrupt:
        logger.info("Сервер остановлен пользователем")
    except Exception as e:
        logger.critical(f"Критическая ошибка при запуске сервера: {e}", exc_info=True)
        raise
