package kz.semey.map;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;

final class AppUi {
  static final int INK = 0xFF263D43, BLUE = 0xFFAFE3F8, MINT = 0xFFE3FFF1;

  static int dp(Context c, int n) {
    return Math.round(n * c.getResources().getDisplayMetrics().density);
  }

  static GradientDrawable round(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(c, radius));
    return d;
  }

  static GradientDrawable wash() {
    return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {BLUE, MINT});
  }

  static TextView text(Context c, String value, int size, int color, boolean bold) {
    TextView v = new TextView(c);
    v.setText(value);
    v.setTextSize(size);
    v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return v;
  }

  static Button button(Context c, String label, int background, int color) {
    Button b = new Button(c);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextColor(color);
    b.setTextSize(15);
    b.setBackground(round(c, background, 24));
    b.setPadding(dp(c, 16), 0, dp(c, 16), 0);
    b.setStateListAnimator(null);
    b.setMinHeight(dp(c, 48));
    return b;
  }

  static LinearLayout column(Context c) {
    LinearLayout l = new LinearLayout(c);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  static LinearLayout.LayoutParams row(Context c, int top) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.topMargin = dp(c, top);
    return p;
  }

  static void bars(Activity a, boolean dark) {
    a.getWindow().setStatusBarColor(dark ? 0xFF191919 : BLUE);
    a.getWindow().setNavigationBarColor(dark ? 0xFF191919 : MINT);
    a.getWindow()
        .getDecorView()
        .setSystemUiVisibility(dark ? 0 : android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    if (android.os.Build.VERSION.SDK_INT >= 27 && !dark)
      a.getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                  | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  static android.graphics.drawable.Drawable icon(Context c, int resource, int size, int color) {
    android.graphics.drawable.Drawable drawable = c.getDrawable(resource).mutate();
    drawable.setTint(color);
    drawable.setBounds(0, 0, dp(c, size), dp(c, size));
    return drawable;
  }

  static void leadingIcon(TextView view, int resource, int color) {
    view.setCompoundDrawablesRelative(
        icon(view.getContext(), resource, 20, color), null, null, null);
    view.setCompoundDrawablePadding(dp(view.getContext(), 10));
  }

  static ImageButton iconButton(Context c, int resource, String label, int background) {
    ImageButton b = new ImageButton(c);
    b.setImageDrawable(icon(c, resource, 24, INK));
    b.setContentDescription(label);
    b.setBackground(round(c, background, 24));
    b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
    b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    b.setMinimumWidth(dp(c, 48));
    b.setMinimumHeight(dp(c, 48));
    return b;
  }
}
