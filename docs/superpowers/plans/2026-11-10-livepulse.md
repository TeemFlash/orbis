# LivePulse Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task-by-task.

**Goal:** Add a separate runnable LivePulse module with anonymous Firebase authentication and realtime Firestore messages.
**Architecture:** Kotlin Compose activity observes MainViewModel state. A repository owns anonymous authentication, a single Firestore listener and immutable message writes. Existing Semey Map remains a separate application.
**Tech Stack:** Android Studio, Kotlin, Compose, Firebase Auth and Firestore.
**Spec:** User-provided LivePulse laboratory specification (2026-11-10), reproduced below.

## Global Constraints
- Module `livepulse`, application ID `kz.semey.livepulse`.
- Automatic currentUser check and signInAnonymously; no login form.
- messages documents: id, text, authorId, timestamp; serverTimestamp only.
- TopAppBar with UID/status, LazyColumn with own/other cards, OutlinedTextField, IconButton, CircularProgressIndicator.
- addSnapshotListener, no pull-to-refresh. English interface per existing user preference.
- Keep existing app and Realtime Database intact.

## Review Focus
- Offline send must not silently discard draft or duplicate messages.
- Authentication failure must offer retry without extra listeners.
- Rotation must preserve session and draft.
- Unauthorized users and forged author/timestamp must be rejected.
- Multiple clients must receive updates without refresh.

### Task 1: Runnable module and session/feed state
Files: root settings.gradle/build.gradle; livepulse/build.gradle.kts; livepulse/src/main/AndroidManifest.xml; Kotlin MainActivity, MainViewModel, PulseRepository.
- [ ] Add repository contract and tests for failed auth, retry, blank send and listener disposal.
- [ ] Run failing tests, implement state transitions and Firebase adapter, rerun.
- [ ] Implement Compose UI and run unit tests, assembleDebug and lintDebug.

### Task 2: Cloud configuration and validation
Files: livepulse/google-services.json, firestore.rules, firebase.livepulse.json, tests/firestore.
- [ ] Register Android app in existing semey-map Firebase project and enable Anonymous provider.
- [ ] Test deny unauthenticated/forged writes; valid server timestamp writes; realtime delivery with isolated emulator.
- [ ] Publish tested rules with any required action-time confirmation.
- [ ] Test simultaneous Android emulator instances if environment permits; record exact evidence and limitations.

### Task 3: Graphs and delivery
Files: graphify-out, docs/LIVEPULSE.md, outputs/LivePulse.apk.
- [ ] Update graph extraction after implementation; document coverage limitations.
- [ ] Review implementation, fix material findings, rebuild and package.
- [ ] Record Firebase setup, launch instructions and actual verification results.

## Execution ledger
- User chose separate module in current project. Proceed inline under existing implementation authorization; no additional plan approval needed.

- Ruling: use existing semey-map Firebase project per user; separate Android package still needs registration. Kotlin 2.0.21 selected to match AGP 8.5 lint metadata support.
