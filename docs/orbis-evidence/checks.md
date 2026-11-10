# Orbis revision verification

User correction: three tabs (Map/Chats/Profile); tilt globe; no Sensors/illuminance/acceleration readings; EXIF-correct profile cover; aligned icons; real chat dates; matching profile dialogs; Orbis visible branding.

| Check | Status |
|---|---|
| Baseline EXIF6 reproduction | FAIL reproduced: encoded240x160 saved240x160 instead of normalized160x240 |
| Corrected EXIF all8 instrumented transforms | PASS: Android instrumentation, all dimensions and corner colors |
| Corrected picker save/relaunch orientation | PASS: actual picker EXIF6 saved160x240, corners blue/red/yellow/green; persisted across APK update |
| Globe tilt controller math/lifecycle | Quaternion/gravity math reviewed by Astra; JS bridge test passes tilt signs, touch priority, pause/reset, invalid values. Source lifecycle registration is foreground/Globe-only. |
| Device globe tilt/drag | PASS reported by user on Samsung S23: four directions and finger drag |
| Three tabs/icons and Orbis branding | PASS: physical phone screenshot, three centered tabs; manifest label Orbis |
| Chat local-day grouping/date/time | PASS: unit edge cases; physical phone shows 10 Nov 2026 divider and individual times |
| Edit profile/Settings visual and keyboard | PASS: editor header and actions above IME; Settings fits landscape; screenshots included |
| Unit tests/build/lint/photo instrumentation | PASS: 21 unit tests, debug APK/test APK build; lint 0 errors, 28 warnings; Android EXIF8/compression instrumentation |

Source frames are synthetic asymmetric corner fixtures, not user photos. No cloud messages are sent during QA. Package/appID remains kz.semey.map to preserve installed account data.

Final source review: Astra, no remaining material findings. Supplied Orbis logo copied unchanged, displayed in auth and launcher. Java 17 used for all final Gradle checks. No Firebase rules or cloud configuration changed.
