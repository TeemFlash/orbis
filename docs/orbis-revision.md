# Orbis revision: Astra design → three Luna implementation owners

User correction supersedes the Sensors/profile addendum in specs/001-motion-environment. Implement directly; no further planning approval needed. Rename visible application to Orbis. Remove Sensors destination, readings, ball, light theme and its setting. Phone tilt now acts on the existing globe itself. Preserve Firebase applicationId `kz.semey.map`, project configuration, authentication, RTDB data/contracts, photos and real map geography. Semey geographical names remain geographical names.

## Exclusive ownership

All paths below are relative to `/home/sam/Documents/UNi/Mob.Dev/Semey Map`.

| Luna owner | Exclusive writable paths |
|---|---|
| Globe | `app/src/main/java/kz/semey/map/MainActivity.java`; `tools/globe/**` (source/build script/template); generated `app/src/main/assets/globe.html`; new `app/src/main/java/kz/semey/map/motion/**`; `app/src/test/java/kz/semey/map/motion/**` |
| Profile | `app/src/main/java/kz/semey/map/profile/**`; `app/src/test/java/kz/semey/map/profile/**` |
| Shell/Chat | `app/src/main/java/kz/semey/map/AppNavigation.java`; `AppUi.java`, `AuthActivity.java`, `ChatActivity.java`, `LocalSettings.java` in that same package; new `app/src/main/java/kz/semey/map/ChatDates.java`; `app/src/test/java/kz/semey/map/ChatDatesTest.java`; `app/src/main/AndroidManifest.xml`; `app/build.gradle`; `app/src/main/res/**`; removal of `app/src/main/java/kz/semey/map/sensors/**` and `app/src/test/java/kz/semey/map/sensors/**` |
| Root/Astra | Documentation, verification, evidence, packaging only |

Do not edit another owner's paths. Shared requests go to Shell/Chat. Do not touch `livepulse/`, Firebase config/rules/data, applicationId, signing, PhotoCodec or custom PhotoCodecInstrumentation runner. All source fixes are written by Luna. Root performs integrated build and device testing.

## Fixed shared contracts

1. `AppNavigation.create(Activity, String selected, BooleanSupplier canLeave)` remains unchanged; selected keys now only `map`, `chats`, `profile`. Preserve pending-chat guard, map anchor and back behavior. Remove SensorActivity reference.
2. `LocalSettings.isGlobeTiltEnabled(Context): boolean` and `LocalSettings.setGlobeTiltEnabled(Context, boolean): void`, public static Java APIs, new preference key `globe_tilt`, default true. Remove old sensor-auto-theme API after deleting sensor callers. Leave obsolete stored preference data harmlessly unused rather than clearing users' preferences.
3. Profile Settings reads/writes this globe setting; MainActivity reads it at each onResume and starts/stops its motion listener accordingly. Only foreground visible Globe collects motion; Map mode, background and other destinations do not.
4. Profile uses platform `android.media.ExifInterface`, already used by existing PhotoCodec.java; follow that existing eight-orientation Matrix mapping without changing PhotoCodec. No EXIF dependency is necessary. Keep Kotlin plugin only if needed by remaining app sources; remove obsolete Compose/lifecycle dependencies only after verifying no remaining app callers. Do not modify root build files or another module for cleanup.
5. English visible branding is Orbis, including launcher label, Auth heading and Profile settings version label. Keep real email/name and no invented account handle or statistics.

## Luna Globe brief

Use a small Java monitor/controller in `motion/`, owned by MainActivity lifecycle. Prefer TYPE_GAME_ROTATION_VECTOR or TYPE_ROTATION_VECTOR; if unavailable/registration fails, use TYPE_GRAVITY. Never use TYPE_LIGHT or an accelerometer-reading UI. No new permission/background service. If orientation and gravity are unavailable, existing touch globe still works; no fake tilt.

Interpret tilt relative to the first valid sample after starting/resuming and after display rotation changes. Convert to current display coordinates before calculating relative horizontal/vertical lean; handle all four rotations, finite values, angular wrap and quaternion normalization if used. Motion is a bounded orientation offset (maximum roughly 0.45 radians each axis), never integrated acceleration or unbounded yaw. Smooth jitter with timestamp-aware low-pass around 0.12–0.18 seconds. Ignore invalid samples. Use SENSOR_DELAY_UI and coalesce outbound JavaScript updates to at most ~30 per second on UI thread. start/stop must be idempotent, stop drops late events, reset calibration on restart.

Edit `tools/globe/entry.js` and rebuild with the existing `tools/globe/build.mjs` pipeline to generate `app/src/main/assets/globe.html`; do not patch bundled/minified WebGL internals. Add narrow JS bridge `window.setDeviceTilt(horizontalRadians, verticalRadians)` in this trusted local globe source. Validate finite inputs and clamp again in JS. Preserve existing `setPoint`, `focusPoint`, `setRunning`, pointer drag, inertia, WebGL engine and geographic marker. Keep touch/base angles separate from motion offsets: renderer uses base phi/theta + smoothed tilt; motion must never permanently accumulate into drag angles. Suppress visible motion offsets during touch drag so touch feels authoritative; smoothly restore afterwards. Reset offsets to zero when disabled/stopped. Existing idle spin may continue, but it must not drown out obvious left/right/up/down tilt. Latitude remains bounded and returning to the neutral phone orientation returns the offset to zero.

Main only sends JS after page is loaded, resumes listener only when active+globeVisible+setting enabled, and stops it in setMapMode(false)/onPause/onDestroy. Ensure initial page load and toggling Globe/Map correctly start the monitor. Preserve location permissions/location marker and map lifecycle. Audit MainActivity icon-only controls for centered equal icon insets; AppUi is supplied by Shell, do not edit it.

Pure tests: neutral baseline, four display rotations, opposite directions, clamp, nonfinite rejection, angular wrap/filter reset. Device check: tilt phone in four directions while globe visible, drag still works, map/tab/background stops listeners, returning recalibrates without jumps. Root distinguishes emulator injection from actual physical tilt.

## Luna Profile brief

Remove all old sensor-auto-theme import/call/text. Keep existing profile hero, rounded bio, local per-UID/guest storage, bounded decode, atomic Os.rename, edit epochs/cancel guards and account reconciliation.

Correct camera photo orientation before creating the saved normalized bitmap: read EXIF from the same bounded source bytes using ExifInterface, decode sampled bitmap, apply all eight orientation mappings including mirrored/transposed orientations. Normal/undefined orientation is unchanged. Apply exactly once, then save JPEG with normalized pixels and no stale orientation. Recycle intermediate bitmap only after successful independent transformed output and never recycle a displayed/staged bitmap. If EXIF cannot be read but image decodes, use orientation normal; invalid image still follows existing error handling. No full-resolution decode. Existing bad saved photos have no recoverable original EXIF after recompression, so do not guess a blanket rotation; reselect original to correct them.

Redesign Edit profile and Settings as cohesive custom rounded dialogs/sheets using existing app palette: sky blue #AFE3F8, mint #E3FFF1, ink #263D43, white fields/cards. Use consistent padding (20–24dp), 16–24dp corner radii, 48dp+ controls, clear title, restrained typography and fully scrollable content under keyboard/landscape/large font. Replace default yellow/stock controls with deliberately tinted/rounded action controls matching app. Edit keeps name/bio/photo + Save/Cancel and existing local-storage disclosure. Show clear selected/loading photo feedback without committing until Save.

Settings contains only functional `Globe follows phone` toggle (LocalSettings bridge), explanatory text that it affects Globe, real account identity and `Orbis · actual version`. No Sensors/auto-theme controls or fake settings. Both dialogs and top-right Settings icon must have centered consistent icon geometry; keep 48dp targets and content descriptions. Match profile button/icon dimensions to shared 24dp icon/48dp target contract.

Tests: all eight EXIF transforms using asymmetric corners or orientation matrix mapping; especially 90/270 plus mirror, without relying on identical symmetric fixture; retain editor epoch/scope tests. Device checks first photo save/relaunch, rotated portrait original, photo Cancel/reopen, account isolation, keyboard and landscape dialog usability.

## Luna Shell/Chat brief

Remove Sensors from shared navigation and manifest, delete obsolete sensors source/tests, remove optional accelerometer/light feature declarations. Declare rotation-vector and gravity hardware optional only if valid Android feature names are used; these sensors do not require feature declarations for access, so omit declarations rather than inventing names. Preserve location optional feature and existing permissions. Keep custom test runner and SDK/Java targets. Implement LocalSettings bridge, update branding to Orbis in owned sources/resources.

Align icons throughout the shared UI: consistent 24dp viewport/bounds for icon-only controls, centered CENTER_INSIDE with symmetric padding and >=48dp touch area; bottom navigation has three equal-width cells, consistent icon box, centered single-line labels and selection semantics. Review Auth inputs/actions, Chat attach/send/back/signout, map shared controls, and profile-owned controls via coordination. Do not merely shift one tab label. Decorative auth artwork may retain intentional composition, but functional icons must align within controls. Avoid changing drawing paths of existing licensed icons unless geometry is actually inconsistent.

Chat date presentation uses existing message `createdAt` epoch milliseconds from Firebase; no cloud schema/query/write changes. Preserve ascending query order and pending-message behavior. Add an inline centered date separator above first valid-timestamp message and whenever local calendar date differs from previous rendered valid-timestamp message. Label with an unambiguous localized absolute date (e.g. 10 Nov 2026), using current device time zone consistently. Each message shows its real local HH:mm time; accessibility may include full date/time. Unknown/pending timestamps show existing Sending… or Time unavailable; never replace with current time or manufacture a separator. For a valid message after unknown timestamps, use nearest preceding valid timestamp to decide date grouping. Photos share same grouping/time logic. Keep separator outside bubble alignment so sent/received styles remain intact. Put date-key/format/grouping helpers in small pure ChatDates class, test midnight/month/year boundary and differing zones, null/nonpositive/invalid timestamps.

Preserve chat photo handling, size guards, Firebase listeners, pending-message navigation block, signout guard and original Map behavior. Do not refactor unrelated messaging flows. Profile owner handles Orbis settings label; Globe owner keeps real Semey geography.

## Integration acceptance

Root runs unit tests, assembleDebug, lintDebug and preserved photo instrumentation. Confirm no visible Sensors destination/reading/auto-theme, launcher and Auth/Profile branding Orbis, three aligned tabs, globe physically responds to phone tilt and stops collection off-screen, touch drag remains correct, rotated JPEG is upright after reselect/save/relaunch, and profile dialogs match app and remain usable with keyboard. Verify real chat date separators and times without sending messages unless previously authorized. Screenshots are actual app UI. Package final APK only after checks; preserve applicationId so existing accounts/data survive update. Document unverified device behavior separately.
