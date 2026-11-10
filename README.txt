ORBIS 2.2 — Android Studio / Lab 9 A+B

Open this directory in Android Studio. Gradle JDK: Java 17.
Compile/target SDK 34; minimum API 26. Package: kz.semey.map.
Build: ./gradlew assembleDebug lintDebug

Interface
- English, blue/mint palette for login, registration, map and chat.
- Bundled Lucide vector icons; licenses in licenses/.
- Globe: bundled Cobe WebGL rendering in a local, network-blocked WebView.
  Drag to rotate. Location button focuses the current position.
- Map: native MapLibre with OpenFreeMap/OpenStreetMap street tiles.
  Actual device city is reverse-geocoded; guest map access is available.
- Semey places shortcut, promotional captions and joke popups removed.
- Three original lab landmarks remain selectable on the street map.
- Errors and sending/offline states are shown only when relevant.

Chat and photos
- Firebase email/password login and registration; shared Orbis lounge.
- Latest 100 messages are listened to in realtime; photos load separately.
- Attach photo opens Android's file picker. Add an optional caption and Send.
- JPEG compression <=100 KiB, max 960px long edge; EXIF metadata is stripped.
- Small display thumbnails and bounded memory cache; tap photo to enlarge/retry.
- Message + photo are written atomically; failed sends retain photo/caption.
- /messages contains metadata, /photos contains encoded compressed images.
- This is an educational small-photo approach, not unlimited original-quality storage.
  Firebase Spark storage/download quotas still apply.

Firebase
- app/google-services.json is configured for semey-map.
- Database: https://semey-map-default-rtdb.firebaseio.com/
- Email/Password enabled. Google sign-in is not implemented.
- database.rules.json published with user approval on 2026-11-03.
- Authentication required for chat/photo reads and own immutable writes.
- Root reads, edits, deletes, oversized images and spoofed sender IDs are denied.

Verification
- See VERIFICATION.txt for actual checks and limits.
- Pure Java test: tests/ChatValidationTest.java.
- Firebase rules test: tests/firebase-rules.cjs (npm install firebase @firebase/rules-unit-testing).
- Device compression test: ./gradlew assembleDebugAndroidTest, install the test APK,
  then adb shell am instrument -w kz.semey.map.test/kz.semey.map.PhotoCodecInstrumentation
- Emulator build option: -PfirebaseEmulators, plus adb reverse for ports 9099/9000.
  Normal builds use cloud Firebase. firebase.json has emulator configuration.

Globe rebuild (optional; prebuilt asset is included)
cd tools/globe && npm ci && node build.mjs

Sources
https://lucide.dev/license
https://github.com/shuding/cobe
https://openfreemap.org/quick_start/
https://www.openstreetmap.org/copyright


2026-11-10 — Orbis revision
Bottom tabs: Map / Chats / Profile. Sensors tab and light-based theme removed.
Globe follows phone orientation while visible; Settings can disable motion.
Touch dragging and the native street map remain available.
Profile cover imports normalize all eight EXIF orientations. Reselect an original
photo to correct a cover that was already saved sideways by an older build.
Edit profile and Settings use the app blue/mint design. Profile is local, scoped
by Firebase UID or guest; editing it does not change cloud chat identity.
Chat displays local-calendar date separators and individual message times.
App ID remains kz.semey.map so the existing installation and account are preserved.
Current APK: outputs/Orbis.apk. Evidence: docs/orbis-evidence/.
Architecture and acceptance: docs/orbis-revision.md.
The earlier sensor-lab spec and docs/sensors-evidence are historical, superseded
by this user-requested revision. Astra reviewed; Luna implemented.
