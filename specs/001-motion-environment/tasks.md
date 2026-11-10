> Superseded by the user-requested Orbis revision (2026-11-10): Sensors tab and light/acceleration UI removed; see ../../docs/orbis-revision.md and ../../docs/orbis-evidence/checks.md. Checked tasks below describe the previous delivery.

# Tasks: Motion & Environment Tracker в Semey Map

Реализация и проверки завершены; фактические результаты и ограничения — в VERIFICATION.txt.
**Input**: spec.md, plan.md, research.md, data-model.md, contracts/sensors-ui.md.

## Phase 1: Setup
- [x] T001 Зафиксировать исходную работоспособность карты/чата/фото и текущий runner в `VERIFICATION.txt`; ничего не удалять из отменённого LivePulse.
- [x] T002 Подключить Kotlin 2.0.21, Compose plugin, Compose BoM 2024.06.00, Activity 1.9.0 и Lifecycle 2.8.2 в `app/build.gradle`; сохранить Java17, SDK34 и testInstrumentationRunner.

## Phase 2: Foundational
- [x] T003 Определить состояние и доступность датчиков в `app/src/main/java/kz/semey/map/sensors/SensorState.kt`: оси — конечные Float в m/s², lux — конечный Float ≥0, значения null до первого измерения, targetX/targetY в [-1;1], Waiting/Active/Missing/RegistrationFailed/Paused отдельно для каждого датчика.
- [x] T004 Реализовать `app/src/main/java/kz/semey/map/sensors/SensorMonitor.kt`: applicationContext, SENSOR_SERVICE, два независимых optional sensor, SENSOR_DELAY_UI, идемпотентные start/stop, копирование скаляров событий, проверка регистрации и игнорирование событий после stop.

## Phase 3: User Story 1 — показания и интеграция (P1)
**Independent test**: Sensors открывается с карты, живые показания видны, отсутствующий датчик обозначен, Back/пауза снимают подписки.
- [x] T005 [US1] Добавить тесты Waiting→Active/Paused, отсутствия и отказа каждого датчика в `app/src/test/java/kz/semey/map/sensors/SensorViewModelTest.kt`; валидный 0 lux отличать от null.
- [x] T006 [US1] Создать `app/src/main/java/kz/semey/map/sensors/SensorViewModel.kt` со StateFlow, скалярными показаниями и явной доступностью, без ссылки Activity и облачной записи.
- [x] T007 [US1] Создать `app/src/main/java/kz/semey/map/sensors/SensorActivity.kt`: ComponentActivity/setContent, onResume start, onPause stop, защитный stop onDestroy; коллекция state учитывает lifecycle.
- [x] T008 [US1] Создать панель X/Y/Z m/s², Illuminance lux и английские состояния в `app/src/main/java/kz/semey/map/sensors/SensorScreen.kt` по contracts/sensors-ui.md.
- [x] T009 [US1] Зарегистрировать exported=false SensorActivity и optional accelerometer/light features в `app/src/main/AndroidManifest.xml`; не добавлять BLE, location или high-sampling permissions для этой функции.
- [x] T010 [US1] Добавить пункт Sensors в `app/src/main/java/kz/semey/map/MainActivity.java` и Lucide activity icon в `app/src/main/res/drawable/ic_activity.xml`; сохранить Map/Chats и доступ гостя.

## Phase 4: User Story 2 — движение шара (P2)
**Independent test**: четыре наклона и четыре rotation, границы шара и устойчивое возобновление после паузы.
- [x] T011 [US2] Добавить тесты конечности, нормирования, clamp [-1;1], time-based фильтра tau=0,15 с и четырёх rotation в `app/src/test/java/kz/semey/map/sensors/SensorMathTest.kt`.
- [x] T012 [US2] Реализовать фильтр и transform в `app/src/main/java/kz/semey/map/sensors/SensorMath.kt`; первое событие после pause инициализирует фильтр; сырые оси не изменять.
- [x] T013 [US2] Подключить target position и сброс фильтра при resume в `app/src/main/java/kz/semey/map/sensors/SensorViewModel.kt`.
- [x] T014 [US2] Добавить шар с плавным смещением и ограничением центра по радиусу/размерам в `app/src/main/java/kz/semey/map/sensors/SensorScreen.kt`; без акселерометра шар остаётся неподвижным.

## Phase 5: User Story 3 — автоматическая тема (P2)
**Independent test**: 10/100 lux, границы 40/60, диапазон 45–55 и отсутствие датчика.
- [x] T015 [US3] Добавить тесты lux ≤40 Dark, lux ≥60 Light, промежуток сохраняет тему, null использует системную, отрицательное/NaN игнорируется в `app/src/test/java/kz/semey/map/sensors/SensorMathTest.kt`.
- [x] T016 [US3] Реализовать гистерезис в `app/src/main/java/kz/semey/map/sensors/SensorMath.kt` и обновление темы в `app/src/main/java/kz/semey/map/sensors/SensorViewModel.kt`.
- [x] T017 [US3] Применить читаемые светлую/тёмную схемы только к Sensors в `app/src/main/java/kz/semey/map/sensors/SensorScreen.kt`.

## Phase 6: Проверка и поставка
- [x] T018 Выполнить `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`, записать результат и существующие предупреждения в `VERIFICATION.txt`; сохранить PhotoCodecInstrumentation.
- [x] T019 Выполнить сценарии `specs/001-motion-environment/quickstart.md`, включая 10 lifecycle-циклов, поворот, отсутствие датчиков и регрессию карты/чата/фото; записать модель устройства и реальные результаты в `VERIFICATION.txt`.
- [x] T020 Сохранить проверенный APK в `outputs/SemeyMap.apk`, инструкцию открытия Sensors в `README.txt`, настоящие скриншоты в `docs/sensors-evidence/`; отдельно указать непроверенное физическое поведение.

## Dependencies & Execution Order
```text
T001–T002 → T003–T004 → US1 (T005–T010)
                            ↓
                       US2 (T011–T014)
                            ↓
                       US3 (T015–T017)
                            ↓
                       T018–T020
```
US1 — минимальный рабочий срез, но полное задание требует US2 и US3. Тесты каждой истории создаются до её логики. Реализацию US2/US3 вести последовательно: они изменяют общие SensorMath, ViewModel и Screen.

## Parallel Opportunities
Исследование официальной документации и чтение текущей навигации можно делать независимо. Обязательных параллельных coding-задач нет; `[P]` намеренно не используется, чтобы не создавать конкурирующие правки общих файлов.

## Implementation Strategy
Сначала рабочая интеграция показаний и корректный lifecycle, затем движение, затем тема, затем проверка всего задания. Удаление LivePulse, BLE, новые облачные сервисы, запись истории и миграция всей карты на Compose не входят в эти задачи.

## Approved Profile tasks and ownership
- [x] T021 [Profile] Build shared four-tab AppNavigation and wire Main/Chat/Sensors/Profile according to orchestration.md; retain pending-message navigation guard and map anchor.
- [x] T022 [Profile] Add ProfileActivity with reference hierarchy, initials placeholder or selected full-bleed photo, actual identity, Edit profile and bio card.
- [x] T023 [Profile] Implement per-UID/guest local profile editing, bounded photo loading, Save/Cancel and picker failure handling; no Firebase mutation.
- [x] T024 [Profile] Implement LocalSettings API and settings dialog with persisted sensor auto-theme, real identity and app version; Sensors re-reads on resume.
- [x] T025 [Profile] Validate edit limits, account isolation, relaunch persistence, picker cancellation, navigation and settings behavior; capture real Profile evidence.

Parallel implementation is explicitly authorized: Luna Sensors owns only sensors source/tests; Luna Shell/Profile owns shared build/manifest/navigation and profile source/tests. Exact boundaries, APIs and integration checks in orchestration.md supersede the earlier statement that no parallel coding tasks are available. T018–T020 run after both ownership tracks complete.

## Verification boundary
T019/T025 completed with device and emulator checks plus unit/source coverage. Real account-switch runtime, accessibility/large-font matrix and manual physical tilt confirmation remain unverified; see VERIFICATION.txt. No BLE or cloud work was performed.
