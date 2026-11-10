# Astra architecture → Luna implementation

Approved scope: existing `app` module, four destinations Map / Chats / Sensors / Profile. English UI. All implementation is assigned to Luna; Astra owns this design. Root coordinates checks, device evidence and packaging. Existing map/globe, Firebase auth, chat and photo behavior remain intact. `livepulse/` and cloud configuration are out of scope.

## Independent ownership

| Owner | Exclusive writable paths |
|---|---|
| Luna Sensors | `app/src/main/java/kz/semey/map/sensors/**`; `app/src/test/java/kz/semey/map/sensors/**` |
| Luna Shell/Profile | `app/build.gradle`; `app/src/main/AndroidManifest.xml`; `app/src/main/java/kz/semey/map/MainActivity.java`; `app/src/main/java/kz/semey/map/ChatActivity.java`; new `app/src/main/java/kz/semey/map/AppNavigation.java`; new `app/src/main/java/kz/semey/map/LocalSettings.java`; `app/src/main/java/kz/semey/map/profile/**`; new profile/navigation drawables under `app/src/main/res/drawable/`; `app/src/test/java/kz/semey/map/profile/**` |
| Root/Astra documentation | `specs/001-motion-environment/**`; `README.txt`; `VERIFICATION.txt`; `outputs/**`; `docs/sensors-evidence/**` |

No agent changes another owner's paths. Report required changes to that owner. Root requests code fixes from Luna rather than writing implementation. Never replace `PhotoCodecInstrumentation` or touch its sources. Root build.gradle already declares Kotlin and Compose plugins; use them from app without changing root configuration. No new dependency for image loading or profile persistence.

## Fixed integration APIs

Shell/Profile provides Java APIs callable directly from Kotlin:

- `public final class LocalSettings` in `kz.semey.map`: `public static boolean isSensorAutoThemeEnabled(Context context)`; `public static void setSensorAutoThemeEnabled(Context context, boolean enabled)`. Application SharedPreferences key `sensor_auto_theme`, default `true`. This setting is device-wide; it is not tied to auth identity.
- `public final class AppNavigation` in `kz.semey.map`: `public static View create(Activity activity, String selected, java.util.function.BooleanSupplier canLeave)`. Valid selected keys: `map`, `chats`, `sensors`, `profile`. `canLeave == null` permits navigation; otherwise evaluate it immediately before leaving and stay when false. Chat supplies `() -> !waitForPendingMessage()` to preserve pending-send protection. Selected destination is marked selected and performs no action.
- Destination classes: `kz.semey.map.sensors.SensorActivity`; `kz.semey.map.profile.ProfileActivity`. Both exported=false.

Navigation renders a compact four-item bottom bar with icons above labels, minimum 48dp touch targets and selected semantics/content descriptions. It must fit narrow phones without horizontal label clipping. Main replaces only addNavigation internals and retains its bottom placement. Chat replaces only its existing two-item nav and retains its send guard. Sensors embeds the shared View using Compose AndroidView in a bottomBar. Profile embeds the same View at the bottom. Account authentication remains required only for Chats, using existing AuthActivity flow.

Switching destinations keeps one Map activity as anchor. Target Map uses CLEAR_TOP | SINGLE_TOP. Other tabs start their destination; finish the previous non-Map tab after successful launch to avoid growing a tab stack. A current MainActivity is retained beneath destinations. Back from Sensors/Profile returns to Map (ensure a map anchor if a destination was entered from a standalone Chat task). Preserve existing Chat back/pending-send behavior; no auth-flow rewrite. No sensor listener can survive switching tabs.

## Luna Sensors brief

Implement T003–T008 and T011–T017 from tasks.md in the owned paths. SensorActivity is ComponentActivity; dependencies and manifest are supplied by Shell/Profile. Compose uses Material3, activity-compose, lifecycle 2.8.2 and StateFlow. Keep sensor acquisition outside composables.

Flow:
```text
SensorManager → SensorMonitor → SensorViewModel StateFlow → SensorScreen
                       ↑                                      ↓
                 Activity resume/pause                 shared navigation
```

- Monitor uses applicationContext, independent TYPE_ACCELEROMETER/TYPE_LIGHT checks and independent registerListener results, SENSOR_DELAY_UI. start/stop are idempotent; active guard drops late events. Copy event scalars. UI pause immediately unregisters; destroy defensively stops. ViewModel never retains Activity.
- Separate Waiting, Active, Missing, RegistrationFailed and Paused state for each sensor. Null means no sample; valid zero lux displays 0. Ignore nonfinite axes and negative/nonfinite lux.
- Display original device-axis X/Y/Z with m/s², lux, state labels, and bounded ball. Raw displayed axes are never low-pass filtered.
- Ball uses independent timestamp-based low-pass filter tau=0.15s. First sample and first after resume initialize filter; reset on resume. Rotate filtered vector into screen coordinates for ROTATION_0/90/180/270; normalize by gravity and clamp [-1,1]. With portrait screen, positive device X moves ball left; positive device Y moves it down. Clamp center using actual viewport minus radius. Animation must not let ball leave bounds during resize.
- Light hysteresis: <=40 dark, >=60 light, middle retains previous result. Before valid light or when unavailable use system theme. Read LocalSettings.isSensorAutoThemeEnabled(this) on every onResume and pass setting into screen/state; when off use system theme while retaining live light measurements. No settings writes in sensor module.
- Screen has readable light blue/mint and dark palettes, concise cards, title Motion & Environment, raw values, status and ball; responsive scrolling for small height/landscape, content does not overlap bottom bar.
- Unit tests cover invalid numbers, zero/null lux, hysteresis boundaries, all rotation mappings, clamp, filtering initialization/resume, and independent status transitions. Prefer pure reducer/math tests; do not add instrumentation runner replacement. Report tests and remaining physical checks honestly.

## Luna Shell/Profile brief

Implement T002, T009–T010 and profile tasks T021–T025. This agent alone edits shared build/manifest/navigation. Enable Kotlin 2.0.21 + Compose, Java/Kotlin target 17, Compose BoM 2024.06.00, material3, activity-compose 1.9.0, lifecycle-viewmodel/runtime-compose 2.8.2 and JUnit; retain SDK34 and exact existing test runner. Register activities and optional accelerometer/light features, add no permissions for sensor or picker.

Profile is a small native Java Views Activity to reuse AppUi and avoid coupling its implementation to sensor Compose internals. A top-right accessible Settings icon, edge-to-edge cover area, dark bottom gradient, centered real name and account label, white rounded Edit profile button, rounded bio card and bottom tabs reproduce reference hierarchy. Use scrollable foreground for small screens/large font. No fabricated follower statistics, follow button, gallery or sample identity.

Default cover is a tasteful generated-in-code gradient with the user's initials, clearly a placeholder. When user selects a photo, display it as the full-bleed cropped cover. Never reuse the reference screenshot as content or download an arbitrary person. Default name: saved local override, otherwise current Firebase displayName, otherwise Guest. Account label may show actual email for signed-in user or Guest · local profile; do not invent @handle. Explain once that edits are saved on this device and do not rename chat identity.

Editing dialog contains display name (trimmed, nonempty, <=60 chars), bio (<=240 chars), change photo, Save/Cancel. Photo selection via ACTION_OPEN_DOCUMENT image/* and persistable read grant or bounded private copy; handle cancellation, invalid image, revoked permission and decode failure without losing prior cover. Decode sampled bitmap off main thread with bounded resolution/memory; no unrestricted full-size decode. New text/photo changes commit only on Save. Scope profile prefs by Firebase UID or guest so another account never inherits personal details; photo storage/read must be equally scoped. Do not call Firebase updateProfile or write RTDB.

Settings opens a working dialog/sheet containing only Auto sensor theme (default enabled; summary states Sensors only and system theme when off), actual signed-in identity/Guest, and actual app version. Persist toggle immediately. Sign-out is optional: implement only if existing safe auth flow can be reused without disturbing pending chat sends; otherwise omit it. No fake notifications/privacy/language controls.

Tests/checks: validate trimming/limits and per-account preference scope; picker Save/Cancel/cancellation/error; settings persists/reloads; map/chats/sensors/profile navigation and pending-chat protection. Keep resources minimal; icons can be new vector drawables consistent with existing licensed Lucide assets.

## Integration and acceptance gate

After both owners finish, root runs `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug`, then device/emulator scenarios and original PhotoCodecInstrumentation. Root routes compilation/runtime fixes back to the relevant Luna owner. Check 10 sensor enter/leave cycles, background pause, rotation, light 10/100 lux and 45–55 lux hysteresis, unavailable sensor paths, and auto-theme toggle. Profile checks include Guest and signed-in identity, edit persistence/relaunch, per-account isolation, photo cancellation and selected full-bleed rendering, settings and narrow-screen navigation. Actual screenshots are evidence; emulator injection is explicitly distinguished from physical sensor proof. Only passing APK is copied to outputs/SemeyMap.apk.
