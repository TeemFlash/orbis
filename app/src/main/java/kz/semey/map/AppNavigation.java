package kz.semey.map;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import java.util.function.BooleanSupplier;

/** Compact shared bottom navigation for the three primary destinations. */
public final class AppNavigation {
  private static final String[] KEYS = {"map", "chats", "profile"};
  private static final String[] LABELS = {"Map", "Chats", "Profile"};
  private static final int[] ICONS = {
    R.drawable.ic_map, R.drawable.ic_messages_square, R.drawable.ic_user
  };
  private static final int INK = 0xFF263D43;

  private AppNavigation() {}

  public static View create(Activity activity, String selected, BooleanSupplier canLeave) {
    LinearLayout bar = new LinearLayout(activity);
    bar.setOrientation(LinearLayout.HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(
        AppUi.dp(activity, 5), AppUi.dp(activity, 4), AppUi.dp(activity, 5), AppUi.dp(activity, 4));
    bar.setBackground(AppUi.round(activity, 0xF0E3FFF1, 26));
    for (int i = 0; i < KEYS.length; i++) {
      final String key = KEYS[i];
      final boolean active = key.equals(selected);
      LinearLayout item = new LinearLayout(activity);
      item.setOrientation(LinearLayout.VERTICAL);
      item.setGravity(Gravity.CENTER);
      item.setMinimumHeight(AppUi.dp(activity, 48));
      item.setPadding(
          AppUi.dp(activity, 2), AppUi.dp(activity, 2), AppUi.dp(activity, 2), AppUi.dp(activity, 2));
      item.setBackground(AppUi.round(activity, active ? 0xCCFFFFFF : Color.TRANSPARENT, 22));
      item.setContentDescription(LABELS[i]);
      item.setFocusable(true);
      item.setClickable(!active);
      item.setSelected(active);

      ImageView icon = new ImageView(activity);
      icon.setImageDrawable(AppUi.icon(activity, ICONS[i], 24, active ? INK : 0xFF597D80));
      icon.setContentDescription(null);
      item.addView(icon, new LinearLayout.LayoutParams(AppUi.dp(activity, 24), AppUi.dp(activity, 24)));
      TextView label = new TextView(activity);
      label.setText(LABELS[i]);
      label.setTextSize(11);
      label.setSingleLine(true);
      label.setGravity(Gravity.CENTER);
      label.setTextColor(active ? INK : 0xFF597D80);
      if (active) label.setTypeface(null, Typeface.BOLD);
      item.addView(label);
      if (!active) item.setOnClickListener(v -> navigate(activity, key, canLeave));
      bar.addView(item, new LinearLayout.LayoutParams(0, AppUi.dp(activity, 52), 1));
    }
    bar.setContentDescription("Main navigation");
    return bar;
  }

  private static void navigate(Activity activity, String destination, BooleanSupplier canLeave) {
    if (canLeave != null && !canLeave.getAsBoolean()) return;
    if ("chats".equals(destination) && FirebaseAuth.getInstance().getCurrentUser() == null) {
      activity.startActivity(new Intent(activity, AuthActivity.class));
      return;
    }
    if ("map".equals(destination)) {
      activity.startActivity(new Intent(activity, MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
      if (!(activity instanceof MainActivity)) activity.finish();
      return;
    }

    Class<?> target;
    if ("chats".equals(destination)) target = ChatActivity.class;
    else target = kz.semey.map.profile.ProfileActivity.class;

    // Chat can be launched directly after authentication. Put the map anchor below tabs first.
    if (activity instanceof ChatActivity) {
      activity.startActivity(new Intent(activity, MainActivity.class)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
    activity.startActivity(new Intent(activity, target));
    if (!(activity instanceof MainActivity)) activity.finish();
  }
}
