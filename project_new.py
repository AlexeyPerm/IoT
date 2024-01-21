#import Adafruit_GPIO.SPI as SPI
#import Adafruit_MCP3008
#import RPi.GPIO as GPIO
import time
from bottle import route, run, template, request

# Конфигурация ADC
#SPI_PORT = 0
#SPI_DEVICE = 0
#mcp = Adafruit_MCP3008.MCP3008(spi=SPI.SpiDev(SPI_PORT, SPI_DEVICE))

# Конфигурация реле
#RELAY_PIN = 17
#GPIO.setmode(GPIO.BCM)
#GPIO.setup(RELAY_PIN, GPIO.OUT)

# Получение температуры с ADC
def get_temperature():
    #adc_value = mcp.read_adc(0)
    #voltage = adc_value * (3.3 / 1023.0)
    #temperature = (voltage - 0.5) * 100
    return 2.5

# Управление реле в зависимости от температуры
def control_heater():
    target_temperature = 50  # Уставка температуры (пример)
    current_temperature = get_temperature()

    if current_temperature < target_temperature:
        GPIO.output(RELAY_PIN, GPIO.HIGH)  # Включение реле
    else:
        GPIO.output(RELAY_PIN, GPIO.LOW)   # Выключение реле

# Веб-интерфейс
@route('/')
def index():
    temperature = get_temperature()
    return template('index', temperature=temperature)

@route('/', method='POST')
def set_target_temperature():
    target_temp = request.forms.get('target_temp')
    # Добавьте здесь код для установки уставки температуры
    return index()

if __name__ == '__main__':
    run(host='0.0.0.0', port=8080, debug=True)
