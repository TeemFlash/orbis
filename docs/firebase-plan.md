# Semey Map Firebase implementation

User-approved scope: add email/password authentication and shared realtime chat to the existing map; remove screamer. Supplied Firebase configuration matches kz.semey.map. Latest visual direction: charcoal login card, sky-blue/mint application with map/chat navigation.

1. Test input validation before implementing it.
2. Add Firebase Auth and Database SDKs, auth and chat activities, and map navigation.
3. Prepare authenticated immutable-message database rules; test with emulator before deploying if console access permits.
4. Build, lint, validate on the authorized phone; distinguish cloud verification from emulator-only verification.

Design: native Java Views, #191919 login background, #292929 card, #373737 fields, white type; main screens #AFE3F8 to #E3FFF1, white message cards, #263D43 text. No fake avatars or dating content. Login is the launcher, with Explore map as guest. Chat uses latest 100 messages; map retains landmarks and geolocation. English copy throughout.

Files: AuthActivity.java, ChatActivity.java, ChatValidation.java, AppUi.java, FirebaseConfig.java; modify MainActivity, manifest and Gradle. Registration stores displayName in Firebase Auth. Messages store uid/name/text/server timestamp. Rules prohibit unauthenticated access, spoofed uid, extra fields and edits/deletes.

Validation: blank/long inputs; permission-denied errors; connection loss; listener cleanup; sender isolation. Existing project is not a git checkout, so backup source under work/firebase/original-src and edit the user-requested directory. No new repository or worktree required.
