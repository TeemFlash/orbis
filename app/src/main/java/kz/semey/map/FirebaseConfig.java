package kz.semey.map;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

final class FirebaseConfig {
  private static boolean configured;

  static synchronized void init() {
    if (configured) return;
    if (BuildConfig.FIREBASE_EMULATORS) {
      FirebaseAuth.getInstance().useEmulator("127.0.0.1", 9099);
      FirebaseDatabase.getInstance().useEmulator("127.0.0.1", 9000);
    }
    configured = true;
  }
}
