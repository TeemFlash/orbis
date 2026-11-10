package kz.semey.map;

import android.content.Context;

/** Preferences shared by the map and profile settings. */
public final class LocalSettings {
  private static final String PREFS = "app_settings";
  private static final String GLOBE_TILT = "globe_tilt";

  private LocalSettings() {}

  public static boolean isGlobeTiltEnabled(Context context) {
    return context.getApplicationContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(GLOBE_TILT, true);
  }

  public static void setGlobeTiltEnabled(Context context, boolean enabled) {
    context.getApplicationContext()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(GLOBE_TILT, enabled)
        .apply();
  }
}
