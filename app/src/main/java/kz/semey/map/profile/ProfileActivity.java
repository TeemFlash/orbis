package kz.semey.map.profile;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.view.Window;
import android.view.WindowManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kz.semey.map.AppNavigation;
import kz.semey.map.LocalSettings;
import kz.semey.map.R;

/** Local-only profile editor. Profile fields never update Firebase identity or chat records. */
public class ProfileActivity extends Activity {
  private static final int PHOTO_REQUEST = 72;
  private static final int BG = 0xFF152128;
  private static final ExecutorService PHOTO_WORKER = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private SharedPreferences profilePrefs;
  private ImageView cover;
  private TextView initials, nameView, accountView, bioView;
  private String scope;
  private Bitmap stagedPhoto;
  private boolean photoLoading;
  private final EditorEpoch editorEpoch = new EditorEpoch();
  private Dialog editorDialog;
  private TextView photoStatus;
  private TextView editorError;
  private int coverLoadGeneration;

  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    bars(true);
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    scope = profileScope(user == null ? null : user.getUid());
    profilePrefs = getSharedPreferences(scope, MODE_PRIVATE);
    buildScreen();
    refresh();
    loadCover();
  }

  @Override protected void onResume() {
    super.onResume();
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    String currentScope = profileScope(user == null ? null : user.getUid());
    if (!currentScope.equals(scope)) {
      if (editorDialog != null && editorDialog.isShowing()) editorDialog.dismiss();
      editorEpoch.invalidate();
      clearStagedPhoto();
      photoLoading = false;
      scope = currentScope;
      profilePrefs = getSharedPreferences(scope, MODE_PRIVATE);
      coverLoadGeneration++;
      cover.setImageDrawable(null);
      initials.setVisibility(View.VISIBLE);
      refresh();
      loadCover();
    } else {
      refresh();
    }
  }

  @Override protected void onDestroy() {
    editorEpoch.invalidate();
    clearStagedPhoto();
    photoLoading = false;
    coverLoadGeneration++;
    super.onDestroy();
  }

  private void buildScreen() {
    FrameLayout shell = new FrameLayout(this);
    shell.setBackgroundColor(BG);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.setClipToPadding(false);
    // Keep the final content above the fixed bottom navigation while allowing it to scroll past it.
    scroll.setPadding(0, 0, 0, dp(68 + 18 + 16));
    LinearLayout content = column();
    content.setPadding(0, 0, 0, dp(18));

    FrameLayout hero = new FrameLayout(this);
    cover = new ImageView(this);
    cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
    cover.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
        new int[] {0xFF173B55, 0xFF597D80, 0xFF18272D}));
    cover.setContentDescription("Profile cover photo");
    hero.addView(cover, new FrameLayout.LayoutParams(-1, -1));
    initials = text("", 46, Color.WHITE, true);
    initials.setGravity(Gravity.CENTER);
    hero.addView(initials, new FrameLayout.LayoutParams(-1, -1));
    View fade = new View(this);
    fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {0x00152128, 0x88202B31, BG}));
    hero.addView(fade, new FrameLayout.LayoutParams(-1, -1));
    ImageButton settings = iconButton(R.drawable.ic_settings, "Settings", 0x77303940);
    settings.setImageDrawable(icon(R.drawable.ic_settings, 24, Color.WHITE));
    settings.setOnClickListener(v -> showSettings());
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
    sp.setMargins(0, dp(16), dp(16), 0);
    hero.addView(settings, sp);

    LinearLayout identity = column();
    identity.setGravity(Gravity.CENTER);
    identity.setPadding(dp(20), 0, dp(20), dp(26));
    nameView = text("", 30, Color.WHITE, true);
    nameView.setGravity(Gravity.CENTER);
    nameView.setMaxLines(2);
    nameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
    identity.addView(nameView);
    accountView = text("", 13, 0xFFD3D8DC, false);
    accountView.setGravity(Gravity.CENTER);
    identity.addView(accountView, row(2));
    Button edit = button("Edit profile", Color.WHITE, BG);
    edit.setOnClickListener(v -> showEditor());
    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(48));
    ep.topMargin = dp(18);
    identity.addView(edit, ep);
    FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    hero.addView(identity, ip);
    content.addView(hero, new LinearLayout.LayoutParams(-1, dp(470)));

    TextView aboutLabel = text("ABOUT", 11, 0xFFB7E7DB, true);
    aboutLabel.setLetterSpacing(.12f);
    LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
    al.setMargins(dp(22), dp(8), dp(22), dp(10));
    content.addView(aboutLabel, al);
    bioView = text("", 15, Color.WHITE, false);
    bioView.setLineSpacing(dp(3), 1f);
    bioView.setPadding(dp(16), dp(15), dp(16), dp(15));
    bioView.setBackground(round(0xAA29343B, 18));
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
    bp.setMargins(dp(18), 0, dp(18), dp(22));
    content.addView(bioView, bp);
    scroll.addView(content);
    shell.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    View nav = AppNavigation.create(this, "profile", null);
    FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, dp(68), Gravity.BOTTOM);
    np.setMargins(dp(20), 0, dp(20), dp(18));
    shell.addView(nav, np);
    setContentView(shell);
  }

  private void refresh() {
    String display = profilePrefs.getString("name", null);
    if (display == null || display.trim().isEmpty()) {
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      display = user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()
          ? user.getDisplayName().trim() : "Guest";
    }
    nameView.setText(display);
    initials.setText(initialsFor(display));
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    accountView.setText(user != null && user.getEmail() != null ? user.getEmail() : "Guest · local profile");
    String bio = profilePrefs.getString("bio", "");
    bioView.setText(bio.isEmpty() ? "Add a bio" : bio);
  }

  private void showEditor() {
    final long editToken = editorEpoch.begin();
    photoLoading = false;
    clearStagedPhoto();
    LinearLayout form = column();
    form.setPadding(dp(20), dp(8), dp(20), 0);
    EditText name = editField("Display name", profilePrefs.getString("name", nameView.getText().toString()), 60);
    EditText bio = editField("Bio", profilePrefs.getString("bio", ""), 240);
    bio.setSingleLine(false);
    bio.setInputType(android.text.InputType.TYPE_CLASS_TEXT
        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    bio.setMinLines(3);
    bio.setMaxLines(5);
    addLabeledField(form, "Display name", name, 0);
    addLabeledField(form, "Bio", bio, 14);
    TextView note = text("Saved on this device. Your chat name is unchanged.", 12, 0xFF536B73, false);
    form.addView(note, row(14));
    Button photo = button("Change photo", 0xFFE3FFF1, BG);
    photo.setContentDescription("Choose a new profile cover photo");
    photo.setOnClickListener(v -> choosePhoto(editToken));
    form.addView(photo, row(14));
    photoStatus = text("No new photo selected", 13, 0xFF536B73, false);
    photoStatus.setPadding(dp(2), dp(5), dp(2), dp(2));
    form.addView(photoStatus, row(4));
    editorError = text("", 13, 0xFF9B3434, false);
    editorError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    form.addView(editorError, row(4));

    ScrollView editorScroll = new ScrollView(this);
    editorScroll.setFillViewport(false);
    editorScroll.setClipToPadding(false);
    editorScroll.addView(form);
    Dialog dialog = new Dialog(this);
    LinearLayout sheet = modalSheet();
    addModalHeader(sheet, "Edit profile", dialog);
    sheet.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(LinearLayout.HORIZONTAL);
    Button cancel = button("Cancel", 0xFFE3FFF1, 0xFF263D43);
    cancel.setOnClickListener(v -> dialog.dismiss());
    Button save = button("Save", 0xFFAFE3F8, 0xFF263D43);
    actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
    LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1);
    saveParams.leftMargin = dp(10);
    actions.addView(save, saveParams);
    LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
    actionParams.topMargin = dp(14);
    sheet.addView(actions, actionParams);
    FrameLayout backdrop = new FrameLayout(this);
    BoundedCardFrame card = new BoundedCardFrame(this, dp(640));
    card.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
    FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
        Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440)), -1, Gravity.CENTER);
    backdrop.addView(card, cardParams);
    dialog.setContentView(backdrop);
    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);
    editorDialog = dialog;
    dialog.setOnDismissListener(d -> {
      if (editorDialog == dialog) editorDialog = null;
      if (editorEpoch.isActive(editToken)) editorEpoch.invalidate();
      clearStagedPhoto();
      photoLoading = false;
      photoStatus = null;
      editorError = null;
    });
    save.setOnClickListener(v -> {
      if (editorError != null) editorError.setText("");
      String cleanName = name.getText().toString().trim();
      String cleanBio = bio.getText().toString().trim();
      String error = validationError(cleanName, cleanBio);
      if (error != null) { if (editorError != null) editorError.setText(error); return; }
      if (!editorEpoch.isActive(editToken)) return;
      if (photoLoading || editorEpoch.hasPendingPicker(editToken)) {
        if (editorError != null) editorError.setText("Wait for the photo to finish loading.");
        return;
      }
      profilePrefs.edit().putString("name", cleanName).putString("bio", cleanBio).apply();
      Bitmap photoCandidate = stagedPhoto;
      stagedPhoto = null;
      refresh();
      dialog.dismiss();
      if (photoCandidate != null) saveCover(photoCandidate);
    });
    dialog.show();
    sizeDialog(dialog, 640, sheet);
  }

  private EditText editField(String hint, String value, int limit) {
    EditText e = new EditText(this);
    e.setText(value);
    e.setHint(hint.equals("Display name") ? "Your name" : "Write a short bio");
    e.setTextSize(15);
    e.setTextColor(0xFF263D43);
    e.setHintTextColor(0xFF6B8188);
    e.setPadding(dp(14), dp(11), dp(14), dp(11));
    e.setMinHeight(dp(52));
    GradientDrawable field = round(Color.WHITE, 16);
    field.setStroke(dp(1), 0xFF9FCAD7);
    e.setBackground(field);
    e.setContentDescription(hint);
    e.setHighlightColor(0x557AB4C6);
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      android.graphics.drawable.Drawable cursor = e.getTextCursorDrawable();
      if (cursor != null) {
        cursor = cursor.mutate();
        cursor.setTint(0xFF31758B);
        e.setTextCursorDrawable(cursor);
      }
    }
    e.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)});
    return e;
  }

  private void addLabeledField(LinearLayout parent, String label, EditText input, int top) {
    LinearLayout group = column();
    TextView title = text(label, 14, 0xFF263D43, true);
    group.addView(title);
    LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
    inputParams.topMargin = dp(7);
    group.addView(input, inputParams);
    parent.addView(group, row(top));
  }

  private void choosePhoto(long editToken) {
    if (!editorEpoch.isActive(editToken)) return;
    Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    pick.setType("image/*");
    pick.addCategory(Intent.CATEGORY_OPENABLE);
    pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    try {
      editorEpoch.startPicker(editToken);
      startActivityForResult(pick, PHOTO_REQUEST);
    }
    catch (RuntimeException e) {
      editorEpoch.cancelPicker(editToken);
      if (photoStatus != null) photoStatus.setText("Photo picker is unavailable.");
    }
  }

  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != PHOTO_REQUEST) return;
    long editToken = editorEpoch.pendingPickerToken();
    if (!editorEpoch.completePicker(editToken)) return;
    if (result != RESULT_OK || data == null || data.getData() == null) {
      if (photoStatus != null) photoStatus.setText(stagedPhoto == null ? "No new photo selected" : "New photo ready · saved with changes");
      return;
    }
    Uri uri = data.getData();
    photoLoading = true;
    if (photoStatus != null) photoStatus.setText("Loading photo…");
    PHOTO_WORKER.execute(() -> {
      Bitmap decoded = null;
      try { decoded = decodeAndNormalize(getContentResolver().openInputStream(uri)); }
      catch (Exception | OutOfMemoryError ignored) { }
      Bitmap resultBitmap = decoded;
      main.post(() -> {
        if (!editorEpoch.isActive(editToken)) {
          if (resultBitmap != null) resultBitmap.recycle();
          return;
        }
        photoLoading = false;
        if (resultBitmap == null) {
          if (photoStatus != null) photoStatus.setText("Could not open this image. Choose another photo.");
        } else {
          replaceStagedPhoto(resultBitmap);
          if (photoStatus != null) photoStatus.setText("New photo ready · saved with changes");
        }
      });
    });
  }

  private void loadCover() {
    File file = coverFile();
    String requestedScope = scope;
    int generation = ++coverLoadGeneration;
    if (!file.isFile()) return;
    PHOTO_WORKER.execute(() -> {
      Bitmap bitmap = null;
      try (InputStream input = new FileInputStream(file)) { bitmap = decodeBounded(input); }
      catch (Exception | OutOfMemoryError ignored) { }
      Bitmap ready = bitmap;
      if (ready != null) main.post(() -> {
        if (!isFinishing() && generation == coverLoadGeneration && requestedScope.equals(scope)) {
          cover.setImageBitmap(ready); initials.setVisibility(View.GONE);
        } else ready.recycle();
      });
    });
  }

  private void saveCover(Bitmap bitmap) {
    File file = coverFile();
    String requestedScope = scope;
    PHOTO_WORKER.execute(() -> {
      File temp = new File(file.getParentFile(), file.getName() + ".tmp");
      try {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new java.io.IOException("mkdir");
        try (FileOutputStream out = new FileOutputStream(temp)) {
          if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)) throw new java.io.IOException("compress failed");
          out.flush();
          out.getFD().sync();
        }
        // POSIX rename replaces an existing destination atomically; a failed rename leaves it intact.
        Os.rename(temp.getAbsolutePath(), file.getAbsolutePath());
        main.post(() -> {
          if (!isFinishing() && requestedScope.equals(scope)) { cover.setImageBitmap(bitmap); initials.setVisibility(View.GONE); }
        });
      } catch (Exception | OutOfMemoryError e) {
        temp.delete();
        main.post(() -> Toast.makeText(this, "Could not save this photo.", Toast.LENGTH_LONG).show());
      }
    });
  }

  private void clearStagedPhoto() {
    Bitmap old = stagedPhoto;
    stagedPhoto = null;
    if (old != null && !old.isRecycled()) old.recycle();
  }

  private void replaceStagedPhoto(Bitmap replacement) {
    Bitmap old = stagedPhoto;
    stagedPhoto = replacement;
    if (old != null && old != replacement && !old.isRecycled()) old.recycle();
  }

  private File coverFile() { return new File(new File(getFilesDir(), "profile-covers"), scope + ".jpg"); }

  static Bitmap decodeBounded(InputStream source) throws java.io.IOException {
    return decodeBytes(readBoundedBytes(source), ExifInterface.ORIENTATION_NORMAL);
  }

  static Bitmap decodeAndNormalize(InputStream source) throws java.io.IOException {
    byte[] bytes = readBoundedBytes(source);
    int orientation = ExifInterface.ORIENTATION_NORMAL;
    try {
      ExifInterface exif = new ExifInterface(new java.io.ByteArrayInputStream(bytes));
      orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    } catch (java.io.IOException | RuntimeException ignored) {
      // If metadata is unreadable but pixels are valid, treat them as already upright.
    }
    return decodeBytes(bytes, orientation);
  }

  private static byte[] readBoundedBytes(InputStream source) throws java.io.IOException {
    if (source == null) throw new java.io.IOException("empty image");
    try (InputStream in = source; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192]; int count; int total = 0;
      while ((count = in.read(buffer)) != -1) {
        total += count;
        if (total > 20 * 1024 * 1024) throw new java.io.IOException("image too large");
        out.write(buffer, 0, count);
      }
      return out.toByteArray();
    }
  }

  private static Bitmap decodeBytes(byte[] bytes, int orientation) throws java.io.IOException {
    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long) bounds.outWidth * bounds.outHeight > 100_000_000L)
      throw new java.io.IOException("invalid image dimensions");
    int sample = 1;
    while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1800) sample *= 2;
    BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) throw new java.io.IOException("decode failed");
    float[] values = orientationMatrixValues(orientation, bitmap.getWidth(), bitmap.getHeight());
    if (!isIdentity(values)) {
      Matrix matrix = new Matrix();
      matrix.setValues(values);
      try {
        Bitmap normalized = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (normalized != bitmap) {
          bitmap.recycle();
          bitmap = normalized;
        }
      } catch (RuntimeException | OutOfMemoryError error) {
        if (!bitmap.isRecycled()) bitmap.recycle();
        throw error;
      }
    }
    return bitmap;
  }

  static float[] orientationMatrixValues(int orientation, int width, int height) {
    switch (orientation) {
      case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
        return new float[] {-1, 0, width, 0, 1, 0, 0, 0, 1};
      case ExifInterface.ORIENTATION_ROTATE_180:
        return new float[] {-1, 0, width, 0, -1, height, 0, 0, 1};
      case ExifInterface.ORIENTATION_FLIP_VERTICAL:
        return new float[] {1, 0, 0, 0, -1, height, 0, 0, 1};
      case ExifInterface.ORIENTATION_TRANSPOSE:
        return new float[] {0, 1, 0, 1, 0, 0, 0, 0, 1};
      case ExifInterface.ORIENTATION_ROTATE_90:
        return new float[] {0, -1, height, 1, 0, 0, 0, 0, 1};
      case ExifInterface.ORIENTATION_TRANSVERSE:
        return new float[] {0, -1, height, -1, 0, width, 0, 0, 1};
      case ExifInterface.ORIENTATION_ROTATE_270:
        return new float[] {0, 1, 0, -1, 0, width, 0, 0, 1};
      case ExifInterface.ORIENTATION_NORMAL:
      case ExifInterface.ORIENTATION_UNDEFINED:
      default:
        return new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }
  }

  private static boolean isIdentity(float[] m) {
    return m[0] == 1 && m[1] == 0 && m[2] == 0 && m[3] == 0 && m[4] == 1 && m[5] == 0;
  }

  static String profileScope(String uid) {
    if (uid == null || uid.trim().isEmpty()) return "profile_guest";
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder("profile_");
      for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b));
      return out.toString();
    } catch (Exception e) { return "profile_" + Integer.toHexString(uid.hashCode()); }
  }

  static String validationError(String name, String bio) {
    if (name == null || name.trim().isEmpty()) return "Enter a display name.";
    if (name.trim().length() > 60) return "Display name must be 60 characters or fewer.";
    if (bio != null && bio.length() > 240) return "Bio must be 240 characters or fewer.";
    return null;
  }

  static String initialsFor(String name) {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) return "?";
    String[] parts = trimmed.split("\\s+");
    return parts.length > 1
        ? (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(java.util.Locale.ROOT)
        : parts[0].substring(0, 1).toUpperCase(java.util.Locale.ROOT);
  }

  /** Small lifecycle token for rejecting picker callbacks after the editor is closed or recreated. */
  static final class EditorEpoch {
    private long generation;
    private long pendingPicker = -1;
    private boolean active;

    long begin() {
      generation++;
      active = true;
      pendingPicker = -1;
      return generation;
    }

    boolean isActive(long token) { return active && generation == token; }

    void startPicker(long token) {
      if (isActive(token)) pendingPicker = token;
    }

    long pendingPickerToken() { return pendingPicker; }

    boolean hasPendingPicker(long token) { return isActive(token) && pendingPicker == token; }

    boolean completePicker(long token) {
      if (token < 0 || !hasPendingPicker(token)) return false;
      pendingPicker = -1;
      return true;
    }

    void cancelPicker(long token) {
      if (hasPendingPicker(token)) pendingPicker = -1;
    }

    void invalidate() {
      generation++;
      active = false;
      pendingPicker = -1;
    }
  }

  private void showSettings() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    Dialog dialog = new Dialog(this);
    LinearLayout sheet = modalSheet();
    addModalHeader(sheet, "Settings", dialog);

    LinearLayout body = column();
    LinearLayout settingRow = new LinearLayout(this);
    settingRow.setGravity(Gravity.CENTER_VERTICAL);
    settingRow.setPadding(dp(14), dp(10), dp(12), dp(10));
    GradientDrawable settingOutline = round(Color.WHITE, 18);
    settingOutline.setStroke(dp(1), 0xFFB5D9E3);
    settingRow.setBackground(settingOutline);
    LinearLayout settingText = column();
    TextView toggleTitle = text("Globe follows phone", 16, 0xFF263D43, true);
    settingText.addView(toggleTitle);
    TextView summary = text("Tilt the globe by moving your device.", 13, 0xFF536B73, false);
    summary.setPadding(0, dp(4), 0, 0);
    settingText.addView(summary);
    settingRow.addView(settingText, new LinearLayout.LayoutParams(0, -2, 1));
    Switch toggle = new Switch(this);
    toggle.setContentDescription("Globe follows phone");
    toggle.setChecked(LocalSettings.isGlobeTiltEnabled(this));
    int[][] states = new int[][] {{android.R.attr.state_checked}, new int[0]};
    toggle.setThumbTintList(new ColorStateList(states, new int[] {0xFF31758B, 0xFFFFFFFF}));
    toggle.setTrackTintList(new ColorStateList(states, new int[] {0xFF8ECBD9, 0xFFC9DDE2}));
    settingRow.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(48)));
    LinearLayout.LayoutParams settingParams = row(2);
    body.addView(settingRow, settingParams);
    toggle.setOnCheckedChangeListener((button, enabled) -> LocalSettings.setGlobeTiltEnabled(this, enabled));

    LinearLayout account = column();
    account.setPadding(dp(2), dp(4), dp(2), 0);
    account.addView(text("Account", 13, 0xFF536B73, true));
    account.addView(text(user != null && user.getEmail() != null ? user.getEmail() : "Guest", 15, 0xFF263D43, false), row(4));
    body.addView(account, row(17));
    String version;
    try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
    catch (Exception e) { version = "Unknown"; }
    body.addView(text("Orbis · " + version, 13, 0xFF536B73, false), row(16));
    int screenHeight = getResources().getDisplayMetrics().heightPixels;
    int bodyMaxHeight = Math.max(dp(140), screenHeight - dp(230));
    MaxHeightScrollView bodyScroll = new MaxHeightScrollView(this, bodyMaxHeight);
    bodyScroll.setFillViewport(false);
    bodyScroll.setClipToPadding(false);
    bodyScroll.addView(body);
    LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
    bodyParams.topMargin = dp(8);
    sheet.addView(bodyScroll, bodyParams);
    dialog.setContentView(sheet);
    dialog.setCancelable(true);
    dialog.setCanceledOnTouchOutside(true);
    dialog.show();
    sizeDialog(dialog, 0);
  }

  private LinearLayout modalSheet() {
    LinearLayout sheet = column();
    sheet.setPadding(dp(20), dp(12), dp(20), dp(20));
    GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[] {0xFFF9FDFE, 0xFFEAF7FA});
    surface.setCornerRadius(dp(26));
    surface.setStroke(dp(1), 0xFF9FCAD7);
    sheet.setBackground(surface);
    return sheet;
  }

  private void addModalHeader(LinearLayout sheet, String title, Dialog dialog) {
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView heading = text(title, 22, 0xFF263D43, true);
    header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
    ImageButton close = iconButton(R.drawable.ic_x, "Close " + title, Color.WHITE);
    close.setImageDrawable(icon(R.drawable.ic_x, 24, 0xFF263D43));
    GradientDrawable closeBackground = round(Color.WHITE, 24);
    closeBackground.setStroke(dp(1), 0xFFB5D9E3);
    close.setBackground(closeBackground);
    close.setMinimumWidth(dp(48));
    close.setMinimumHeight(dp(48));
    close.setOnClickListener(v -> dialog.dismiss());
    header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
    sheet.addView(header);
  }

  private void sizeDialog(Dialog dialog, int maxHeightDp) {
    sizeDialog(dialog, maxHeightDp, null);
  }

  private void sizeDialog(Dialog dialog, int maxHeightDp, View heightRoot) {
    Window window = dialog.getWindow();
    if (window == null) return;
    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    WindowManager.LayoutParams attributes = window.getAttributes();
    attributes.dimAmount = .36f;
    window.setAttributes(attributes);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    int screenWidth = getResources().getDisplayMetrics().widthPixels;
    int width = Math.min(screenWidth - dp(32), dp(440));
    if (maxHeightDp <= 0) {
      window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    } else {
      // A non-floating, resize-adjusted window gives the bounded card the actual
      // height left above the IME. No inset-driven window resizing is needed.
      window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
    }
  }

  private static final class BoundedCardFrame extends FrameLayout {
    private final int maxHeight;

    BoundedCardFrame(Activity context, int maxHeight) {
      super(context);
      this.maxHeight = maxHeight;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int mode = MeasureSpec.getMode(heightMeasureSpec);
      int available = mode == MeasureSpec.UNSPECIFIED ? maxHeight : MeasureSpec.getSize(heightMeasureSpec);
      int bounded = Math.max(1, Math.min(maxHeight, available));
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(bounded, MeasureSpec.EXACTLY));
    }
  }

  private static final class MaxHeightScrollView extends ScrollView {
    private final int maxHeight;

    MaxHeightScrollView(Activity context, int maxHeight) {
      super(context);
      this.maxHeight = maxHeight;
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      int mode = MeasureSpec.getMode(heightMeasureSpec);
      int available = mode == MeasureSpec.UNSPECIFIED ? maxHeight : MeasureSpec.getSize(heightMeasureSpec);
      int bounded = Math.min(available, maxHeight);
      super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(bounded, MeasureSpec.AT_MOST));
    }
  }

  private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
  private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
  private TextView text(String value, int size, int color, boolean bold) {
    TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
    if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return v;
  }
  private LinearLayout.LayoutParams row(int top) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); return p;
  }
  private GradientDrawable round(int color, int radius) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
  }
  private Button button(String label, int background, int color) {
    Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(color); b.setTextSize(15);
    b.setBackground(round(background, 24)); b.setPadding(dp(16), 0, dp(16), 0); b.setStateListAnimator(null); b.setMinHeight(dp(48)); return b;
  }
  private android.graphics.drawable.Drawable icon(int resource, int size, int color) {
    android.graphics.drawable.Drawable d = getDrawable(resource).mutate(); d.setTint(color);
    d.setBounds(0, 0, dp(size), dp(size)); return d;
  }
  private ImageButton iconButton(int resource, String label, int background) {
    ImageButton b = new ImageButton(this); b.setImageDrawable(icon(resource, 24, Color.WHITE));
    b.setContentDescription(label); b.setBackground(round(background, 24));
    b.setPadding(dp(12), dp(12), dp(12), dp(12)); b.setScaleType(ImageView.ScaleType.CENTER_INSIDE); return b;
  }
  private void bars(boolean dark) {
    getWindow().setStatusBarColor(dark ? 0xFF191919 : 0xFFAFE3F8);
    getWindow().setNavigationBarColor(dark ? 0xFF191919 : 0xFFE3FFF1);
    getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }
}
