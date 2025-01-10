
from bottle import Bottle, template, request, route, run
from gpiozero import MCP3008, OutputDevice
import smtplib
from email.mime.text import MIMEText

EMAIL_ADDRESS = "EMAIL_ADDRESS@my_mail.ru"
EMAIL_PASSWORD = "MY_PASSWORD"
DST_EMAIL_ADDRESS = "DST_EMAIL_ADDRESS@my_mail.ru"

# Параметры для управления реле и термодатчиком
relay_pin = 17  # Пин, к которому подключено реле
setpoint = 25.0  # Уставка температуры в градусах Цельсия
relay = OutputDevice(relay_pin)
adc = MCP3008(0)  # Канал АЦП, к которому подключено термосопротивление

def send_email_notification(subject, message):
    try:
        msg = MIMEText(message)
        msg['Subject'] = subject
        msg['From'] = EMAIL_ADDRESS
        msg['To'] = DST_EMAIL_ADDRESS

        # Подключение к гугловскому SMTP-серверу (тестировал именно со своего аккаунта)
        with smtplib.SMTP('smtp.gmail.com', 587) as server:
            server.starttls()   #шифрование TLS
            server.login(EMAIL_ADDRESS, EMAIL_PASSWORD) #логинимся на сервер
            server.send_message(msg)

        print("Email notification sent.")
    except Exception as e:
        print(f"Failed to send email: {e}")

def read_temperature():
    try:
        # Чтение значения с АЦП и преобразование его в температуру (пример на ТСП 100, подключенный к АЦП MCP3008 через делитель напряжения)
        adc_value = adc.value
        voltage = adc_value * 3.3  # Напряжение АЦП 0...3.3 В
        lowV = (3.3*39.225)/(50+39.225)
        highV = (3.3*92.775)/(50+92.775)
        temperature_celsius = (((voltage - lowV) / (highV - lowV))**2) * (500 - (-50))+(-50)
        return round(temperature_celsius, 2)
    except Exception as e:
        print("Error reading temperature: {}".format(e))
        return None

def control_heating_relay(target_temperature):
    current_temperature = read_temperature()
    if current_temperature is not None and current_temperature < target_temperature:
            relay.on()
            if current_temperature <= target_temperature - 5:
                send_email_notification("Temperature Alert", f"Temperature is too low: {current_temperature}°C")
        else:
            relay.off()

@route('/')
def index():
    temperature = read_temperature()
    return template("index.html", temperature=temperature, setpoint=setpoint)

@route("/setpoint", method="POST")
def set_setpoint():
    new_setpoint = request.forms.get("setpoint")
    try:
        new_setpoint = float(new_setpoint)
        global setpoint
        setpoint = new_setpoint
    except ValueError:
        print("Ошибка конвертации в float")
    control_heating_relay(setpoint)
    return template("index", temperature=read_temperature(), setpoint=setpoint)

if __name__ == "__main__":
    run(host='localhost', port=8080)

