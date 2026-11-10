# Research: решения для интеграции

## 1. Kotlin/Compose внутри Java приложения
**Decision**: Kotlin ComponentActivity в `app`, запуск Intent из MainActivity; сохранить Java-экраны.
**Rationale**: Изолирует новый экран и выполняет стек задания без миграции карты.
**Alternatives considered**: ComposeView в текущей Activity усложняет её lifecycle; отдельное приложение противоречит новому запросу.
Источники: [Compose interoperability](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views), [compiler/dependencies](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).

## 2. Датчики и разрешения
**Decision**: TYPE_ACCELEROMETER и TYPE_LIGHT, SENSOR_DELAY_UI; getDefaultSensor/null и boolean registerListener проверять отдельно. Optional uses-feature, без runtime permissions.
**Rationale**: Обычное чтение этих датчиков не требует Bluetooth/location permissions или HIGH_SAMPLING_RATE_SENSORS. Их отсутствие не должно запрещать установку.
**Alternatives considered**: высокочастотное чтение, гироскоп и BLE не нужны описанным сценариям.
Источник: [Sensors overview](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview).

## 3. Владение подписками
**Decision**: onResume стартует, onPause останавливает; stop повторяемый; UI flow lifecycle-aware.
**Rationale**: Регистрация при recomposition создаёт дубликаты; ViewModel.onCleared недостаточен для прекращения работы в фоне.
**Alternatives considered**: фоновый Service не соответствует заданию и расходует батарею.
Источник: [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager).

## 4. Сырые показания и анимация
**Decision**: значения UI в системе координат устройства, гравитация включена; шар использует отдельный сглаженный и повёрнутый в экранные координаты сигнал.
**Rationale**: Оси датчика не меняются автоматически при повороте дисплея. Raw values нельзя называть скоростью или расстоянием.
**Alternatives considered**: двойное интегрирование ускорения добавляет дрейф и не требуется.
Источники: [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [coordinate system](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-coords).

## 5. Тема и проверка
**Decision**: пороги 40/60 lux, системная тема при отсутствии света; программные тесты плюс физическая проверка по возможности.
**Rationale**: Разные пороги предотвращают мерцание на границе. Эти числа — наше проектное допущение, не стандарт Android.
**Alternatives considered**: один порог вызывает дрожание темы; ручной выбор темы — лишняя функция для этого задания.
Источник: [Sensor best practices](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview#sensors-practices).

Исследование выполнено по официальной Android-документации и текущим app/build.gradle, AndroidManifest.xml, MainActivity.java; существующий graphify-out использован для навигационных/lifecycle связей. Неизвестных, блокирующих план, нет. Наличие датчиков телефона проверяется на этапе реализации.
