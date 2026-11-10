# Implementation Plan: Motion & Environment Tracker в Semey Map

**Branch**: нет Git-репозитория; идентификатор feature `001-motion-environment` | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)
**Input**: `specs/001-motion-environment/spec.md`
**Stage**: План одобрен; реализация разрешена с дополнением Profile.

## Summary
Добавить экран Sensors в существующий модуль `app`, package `kz.semey.map`. В нижнюю навигацию добавить Sensors и Profile: Map / Chats / Sensors / Profile. Sensors открывает Kotlin `SensorActivity` через Intent; Profile использует небольшую Java Views Activity. Новая Activity использует Compose и ViewModel, остальные экраны сохраняются. Два внутренних датчика обеспечивают показания, движение шара и автоматическую тему.

## Technical Context
**Language/Version**: Kotlin 2.0.21 для новых файлов, существующая Java 17.
**Primary Dependencies**: Android SensorManager; Compose BoM 2024.06.00, activity-compose 1.9.0, lifecycle-viewmodel-compose/runtime-compose 2.8.2; версии совместимы с текущим compileSdk 34 и уже проверялись сборкой другого модуля, но интеграция в app требует собственной проверки.
**Storage**: показания — StateFlow в памяти; локальный профиль и настройка автотемы — SharedPreferences/приватное фото. Firebase для этих данных не используется.
**Testing**: JUnit для фильтрации/порогов/ориентации, lifecycle/instrumentation проверки без замены существующего PhotoCodecInstrumentation, эмулятор Virtual sensors и телефон.
**Target Platform**: Android minSdk 26, compileSdk/targetSdk 34, AGP 8.5.0, Gradle 8.7.
**Project Type**: существующее Android-приложение с Java Views и новым Kotlin Compose экраном.
**Performance Goals**: обработка SENSOR_DELAY_UI; реакция UI не позднее 0,5 с после принятого события в проверке. Частота задаётся системой, не гарантируется точный период.
**Constraints**: никакого фонового сервиса, BLE, новых runtime permissions, истории или облачной отправки. Существующий app test runner сохраняется.
**Scale/Scope**: один экран, два датчика, один локальный StateFlow.

## Constitution Check
До исследования и после проектирования: PASS.
- Объём: только задание по внутренним датчикам в `app`.
- Сохранение поведения: Java карта/глобус, вход, чат и фото не переписываются.
- Lifecycle: onResume → регистрация, onPause → unregisterListener; onDestroy — защитный stop.
- Достоверность: Waiting / Unavailable / Error отличаются от настоящего 0.
- Проверка: план содержит программные проверки и отдельный физический сценарий; фактическая реализация не заявляется.

## Project Structure
### Documentation (this feature)
```text
specs/001-motion-environment/
  spec.md
  plan.md
  research.md
  data-model.md
  contracts/sensors-ui.md
  quickstart.md
  tasks.md
  checklists/requirements.md
```
### Source Code (repository root, planned changes)
```text
app/build.gradle                                   # Kotlin/Compose и test dependencies
app/src/main/AndroidManifest.xml                    # SensorActivity, optional features
app/src/main/java/kz/semey/map/MainActivity.java    # пункт Sensors
app/src/main/java/kz/semey/map/sensors/
  SensorActivity.kt                                # lifecycle и Compose entry
  SensorMonitor.kt                                 # SensorEventListener, start/stop
  SensorViewModel.kt                               # StateFlow и обработка событий
  SensorState.kt                                   # значения и доступность
  SensorMath.kt                                    # фильтр, ориентация, тема, clamp
  SensorScreen.kt                                  # показания, шар, цветовая схема
app/src/main/res/drawable/ic_activity.xml            # Lucide icon, существующая лицензия
app/src/test/java/kz/semey/map/sensors/
  SensorMathTest.kt
  SensorViewModelTest.kt
```
**Structure Decision**: новая Activity в `app` — минимальная граница Java/Compose. Новый Gradle-модуль, приложение или DI framework не требуются.

## Порядок реализации
1. Подключить в `app` уже доступные root plugins Kotlin/Compose и зависимости. Сначала проверить совместную компиляцию Java/Kotlin.
2. Создать SensorMonitor с applicationContext.getSystemService(Context.SENSOR_SERVICE). Независимо получить TYPE_ACCELEROMETER и TYPE_LIGHT; проверить null и результат registerListener. start()/stop() идемпотентны, подписки регистрируются только в onResume и снимаются в onPause. Флаг active защищает от запоздавших событий после stop.
3. Копировать SensorEvent.values в собственные скаляры. Передавать сырые X/Y/Z (с гравитацией) и lux в ViewModel, публиковать неизменяемый StateFlow. Не сохранять ссылку SensorEvent или Activity в ViewModel.
4. Создать Compose-панель с подписями X/Y/Z, m/s², Illuminance, lux, состояниями датчиков и Back. collectAsStateWithLifecycle управляет UI-подпиской; сам по себе он не заменяет unregisterListener.
5. Для шара отдельно сглаживать ускорение: time-based low-pass, tau = 0,15 с, первое событие и событие после resume инициализируют фильтр. Преобразовывать в экранные оси по текущему rotation, нормировать на g, clamp в [-1;1]; вычислять смещение в пикселях в пределах half-size minus radius. Не вычислять пройденное расстояние из ускорения.
6. Добавить гистерезис: lux ≤40 → Dark, lux ≥60 → Light, иначе сохранить прошлую тему. До первого события — системная тема. Отрицательные и нечисловые данные не использовать. Пороги — проектные значения, при физической проверке могут быть уточнены с фиксацией в документации.
7. Встроить Sensors в нижнюю навигацию MainActivity. Экран доступен также гостю карты, отдельного входа не требует. Возврат через finish сохраняет существующий экземпляр карты.
8. Выполнить unit tests, assembleDebug, lintDebug и сценарии quickstart.md. Записать наличие реальных датчиков, измерения, lifecycle-проверку, регрессии карты/чата. Только после этого считать интеграцию выполненной.

## Границы и риски
- Автотема применяется к Sensors. Общее переоформление карты/чата не включено.
- BluetoothAdapter упомянут в заголовке стека, но BLE-сканирование/подключение и устройство в функциональных требованиях отсутствуют. Их не придумываем.
- На устройстве может не быть TYPE_LIGHT. Приложение показывает это явно; имитация в тесте не доказывает физический датчик.
- Перед внедрением проверить реальные версии плагинов и existing runner. Не заменять runner ради новых тестов.
- Остановленная работа LivePulse уже оставила модуль и облачные настройки. Они не используются этим экраном; удаление не включено и должно быть отдельным решением.

## Complexity Tracking
Нарушений конституции нет. Слой SensorMonitor необходим для явного lifecycle, SensorMath — для проверяемых преобразований; дополнительных сервисов и репозиториев не требуется.

## Approved Profile architecture addendum
Profile, Settings and shared navigation are specified in [orchestration.md](orchestration.md), including disjoint Luna ownership and exact Java/Kotlin APIs. Sensor implementation remains in its own package. Shell/Profile owner supplies all Gradle, manifest and existing Activity edits. Default cover is initials/gradient; selected user photo becomes the full-bleed cover. Profile is local and scoped by UID/guest, with no social backend. The auto-theme switch defaults on and is re-read when Sensors resumes. This addendum supersedes earlier single-screen scope statements only for the expressly approved Profile/settings/navigation work.
