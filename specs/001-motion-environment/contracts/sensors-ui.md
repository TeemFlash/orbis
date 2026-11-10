# UI contract

- Entry: MainActivity bottom navigation, Sensors, доступно гостю и авторизованному пользователю.
- Destination: kz.semey.map.sensors.SensorActivity, exported=false; Back возвращает существующую карту.
- Title: Motion & Environment. Labels: Accelerometer, X, Y, Z, m/s², Illuminance, lux.
- No sample: Waiting for sensor; no hardware: Not available on this device; registration failure: Sensor unavailable; paused: Paused. Число 0 отображается только из валидного измерения.
- Ball: отдельная область с границами, поддержка portrait/landscape и ограничение по радиусу; текстовые показания остаются доступной альтернативой анимации.
- Theme: светлая голубая/мятная и контрастная тёмная палитры. Тема определяется состоянием света только на этом экране.
- SensorMonitor.start()/stop(): идемпотентны, без Activity reference; после stop callback не обновляет state.
- SensorViewModel: StateFlow<SensorUiState>, принимает копии скаляров и состояние доступности; не регистрирует датчики при каждой Compose recomposition.
