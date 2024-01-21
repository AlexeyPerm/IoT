<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Печь</title>
</head>
<body>
    <h1>Текущая температура: {{ temperature }}°C</h1>
    <form action="/" method="post">
        <label for="target_temp">Уставка температуры:</label>
        <input type="number" id="target_temp" name="target_temp" required>
        <input type="submit" value="Установить">
    </form>
</body>
</html>
