
import json
import smtplib
from email.mime.text import MIMEText

from bottle import template, request, route, run
from gpiozero import MCP3008, OutputDevice

# Параметры для управления реле и термодатчиком
relay_pin = 17  # Пин, к которому подключено реле
setpoint = 25.0  # Уставка температуры в градусах Цельсия
relay = OutputDevice(relay_pin)
adc = MCP3008(0)  # Канал АЦП, к которому подключено термосопротивление


def send_email_notification(subject, message):
    try:
        # Данные для авторизации берутся из JSON-файла cred.json
        with open('cred.json') as cred:
            credentials = json.load(cred)
        src_email_address = credentials["SRC_EMAIL_ADDRESS"]
        email_password = credentials["EMAIL_PASSWORD"]
        dst_email_address = credentials["DST_EMAIL_ADDRESS"]

        msg = MIMEText(message)
        msg['Subject'] = subject
        msg['From'] = src_email_address
        msg['To'] = dst_email_address

        # Подключение к гугловскому SMTP-серверу (тестировал именно со своего аккаунта)
        with smtplib.SMTP('smtp.gmail.com', 587) as server:
            server.starttls()  # шифрование TLS
            server.login(src_email_address, email_password)  # логинимся на сервер
            server.send_message(msg)

        print("Письмо успешно отправлено")
    except Exception as e:
        print(f"Ошибка при отправке письма: {e}")


# Функция возвращает считанное значение температуры с датчица АЦП (Аналого-цифровой преобразователь)
def read_temperature():
    try:
        # Чтение значения с АЦП и преобразование его в температуру
        # (пример на ТСП 100, подключенный к АЦП MCP3008 через делитель напряжения)
        adc_value = adc.value
        voltage = adc_value * 3.3  # Напряжение АЦП 0...3.3 В
        lowV = (3.3 * 39.225) / (50 + 39.225)
        highV = (3.3 * 92.775) / (50 + 92.775)
        temperature_celsius = (((voltage - lowV) / (highV - lowV)) ** 2) * (500 - (-50)) + (-50)
        return round(temperature_celsius, 2)
    except Exception as e:
        print("Error reading temperature: {}".format(e))
        return None


# Функция для контроля за температурой
def control_heating_relay(target_temperature):
    current_temperature = read_temperature()
    if current_temperature is not None and current_temperature < target_temperature:
        relay.on()
        send_email_notification("Реле включено", "Произошло включение реле")
        if current_temperature <= target_temperature - 10:
            send_email_notification("Внимание: температура ", f"Низкая температура: {current_temperature}°C")
        else:
            relay.off()
            send_email_notification("Реле отключено", "Произошло отключение реле")


@route('/')
def index():
    temperature = read_temperature()
    return template("index.html", temperature=temperature, setpoint=setpoint)


@route("/setpoint", method="POST")
def set_setpoint():
    # Ограничение: setpoint = number, min = 200, max = 1500. Задаётся в index.html
    new_setpoint = request.forms.get("setpoint")
    try:
        new_setpoint = float(new_setpoint)
        global setpoint
        setpoint = new_setpoint
    except ValueError:
        print("Ошибка конвертации в float")
    control_heating_relay(setpoint)
    return template("index", temperature=read_temperature(), setpoint=setpoint)

@route('/current_temperature')
def get_current_temperature():
    temperature = read_temperature()
    if temperature is not None:
        response.content_type = 'application/json'  # Устанавливаем тип контента как JSON
        return json.dumps({"temperature": temperature})
    else:
        response.status = 500  # Ошибка сервера
        return json.dumps({"error": "Не удалось получить температуру"})
        
if __name__ == "__main__":
    run(host='localhost', port=8080)
