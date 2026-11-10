# Data model (только память)

| Состояние | Поля и ограничения |
|---|---|
| SensorAvailability | Waiting, Active, Missing, RegistrationFailed, Paused; отдельно для каждого датчика |
| AccelerationReading | x/y/z: конечные Float, m/s², включая гравитацию; timestampNanos: монотонное время события; null до первого измерения |
| LightReading | lux: конечный Float ≥0; null до первого измерения; timestampNanos |
| SensorUiState | acceleration, light, availability, targetX/targetY в [-1;1], theme Dark/Light; до света тема системы |

Raw values не сглаживаются для чисел. Для шара хранится отдельный фильтр; его время сбрасывается после паузы. Положение шара пересчитывается по размерам UI, центр не выходит за границы с учётом радиуса. Измерения не сохраняются на диск и не отправляются в Firebase.

Lifecycle: created → resume/register → Waiting → Active → pause/unregister → Paused; resume начинает новые измерения. Missing и RegistrationFailed не дают фиктивного нуля. После pause старые значения допускается показать только как Paused, не как live.

Тема: system default → при lux ≤40 Dark; при lux ≥60 Light; 40<lux<60 сохраняется прежнее значение. Поворот дисплея меняет только transform визуализации, а не определения X/Y/Z.
