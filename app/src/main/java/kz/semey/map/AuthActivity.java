package kz.semey.map;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;

/** Email/password entry screen, using the supplied sky-blue and mint reference. */
public class AuthActivity extends Activity {
  private EditText name, email, password;
  private Button submit, switchMode;
  private TextView error, title;
  private boolean registering, busy, navigated;
  private FirebaseAuth auth;
  private Task<?> pendingAuth;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    FirebaseConfig.init();
    auth = FirebaseAuth.getInstance();
    if (auth.getCurrentUser() != null) {
      openChat();
      return;
    }
    registering = state != null && state.getBoolean("registering");
    Object retained = getLastNonConfigurationInstance();
    if (retained instanceof Task) pendingAuth = (Task<?>) retained;
    AppUi.bars(this, false);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackground(AppUi.wash());
    LinearLayout outer = AppUi.column(this);
    outer.setGravity(Gravity.CENTER);
    outer.setPadding(d(24), d(24), d(24), d(24));
    scroll.addView(outer);
    ImageView brand = new ImageView(this);
    brand.setImageResource(R.drawable.orbis_logo);
    brand.setContentDescription("Orbis");
    brand.setScaleType(ImageView.ScaleType.FIT_CENTER);
    brand.setAdjustViewBounds(true);
    LinearLayout.LayoutParams brandSize = new LinearLayout.LayoutParams(d(120), d(120));
    brandSize.gravity = Gravity.CENTER_HORIZONTAL;
    outer.addView(brand, brandSize);
    FrameLayout hero = new FrameLayout(this);
    ImageView mapArt = new ImageView(this);
    mapArt.setImageDrawable(AppUi.icon(this, R.drawable.ic_map, 62, 0xFF4A8592));
    mapArt.setPadding(d(24), d(24), d(24), d(24));
    mapArt.setBackground(AppUi.round(this, 0xAAFFFFFF, 26));
    mapArt.setRotation(-10);
    FrameLayout.LayoutParams mapArtParams =
        new FrameLayout.LayoutParams(d(116), d(126), Gravity.CENTER);
    mapArtParams.setMargins(0, 0, d(60), 0);
    hero.addView(mapArt, mapArtParams);
    ImageView chatArt = new ImageView(this);
    chatArt.setImageDrawable(AppUi.icon(this, R.drawable.ic_messages_square, 40, 0xFF3B756B));
    chatArt.setPadding(d(22), d(22), d(22), d(22));
    chatArt.setBackground(AppUi.round(this, 0xFFDDF7ED, 24));
    chatArt.setRotation(10);
    FrameLayout.LayoutParams chatArtParams =
        new FrameLayout.LayoutParams(d(92), d(104), Gravity.CENTER);
    chatArtParams.setMargins(d(108), d(32), 0, 0);
    hero.addView(chatArt, chatArtParams);
    hero.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    outer.addView(hero, new LinearLayout.LayoutParams(-1, d(170)));
    title = AppUi.text(this, "Welcome back", 30, AppUi.INK, true);
    title.setGravity(Gravity.CENTER);
    outer.addView(title, AppUi.row(this, 4));
    LinearLayout card = AppUi.column(this);
    card.setPadding(d(18), d(18), d(18), d(18));
    card.setBackground(AppUi.round(this, 0xAAFFFFFF, 28));
    outer.addView(card, AppUi.row(this, 26));
    name = input("Your name", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    name.setId(R.id.auth_name);
    AppUi.leadingIcon(name, R.drawable.ic_user, 0xFF597D80);
    card.addView(name, AppUi.row(this, 0));
    email = input("Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    email.setId(R.id.auth_email);
    email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS);
    AppUi.leadingIcon(email, R.drawable.ic_mail, 0xFF597D80);
    card.addView(email, AppUi.row(this, 12));
    password =
        input("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    password.setId(R.id.auth_password);
    password.setSaveEnabled(false);
    password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
    AppUi.leadingIcon(password, R.drawable.ic_lock_keyhole, 0xFF597D80);
    card.addView(password, AppUi.row(this, 12));
    error = AppUi.text(this, "", 13, 0xFF9B3434, false);
    error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    card.addView(error, AppUi.row(this, 8));
    submit = AppUi.button(this, "Sign in", AppUi.INK, Color.WHITE);
    submit.setPadding(d(20), d(12), d(20), d(12));
    submit.setOnClickListener(v -> authenticate());
    card.addView(submit, AppUi.row(this, 8));
    switchMode =
        AppUi.button(this, "Don't have an account? Sign up", Color.TRANSPARENT, 0xFF375B60);
    switchMode.setTextSize(13);
    switchMode.setElevation(0);
    switchMode.setStateListAnimator(null);
    switchMode.setOnClickListener(
        v -> {
          registering = !registering;
          updateMode();
        });
    outer.addView(switchMode, AppUi.row(this, 12));
    Button guest = AppUi.button(this, "Explore the map", 0xAAFFFFFF, AppUi.INK);
    guest.setPadding(d(20), 0, d(20), 0);
    AppUi.leadingIcon(guest, R.drawable.ic_compass, AppUi.INK);
    guest.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
    outer.addView(guest, AppUi.row(this, 8));
    setContentView(scroll);
    updateMode();
  }

  private int d(int n) {
    return AppUi.dp(this, n);
  }

  private EditText input(String hint, int type) {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setHint(hint);
    e.setContentDescription(hint);
    e.setInputType(type);
    e.setTextSize(15);
    e.setTextColor(AppUi.INK);
    e.setHintTextColor(0xFF59757A);
    e.setPadding(d(16), d(12), d(16), d(12));
    e.setBackground(AppUi.round(this, Color.WHITE, 18));
    e.setMinimumHeight(d(54));
    return e;
  }

  private void updateMode() {
    title.setText(registering ? "Join Orbis" : "Welcome back to Orbis");
    AppUi.leadingIcon(
        submit, registering ? R.drawable.ic_user_plus : R.drawable.ic_log_in, Color.WHITE);
    name.setVisibility(registering ? View.VISIBLE : View.GONE);
    submit.setText(registering ? "Create account" : "Sign in");
    switchMode.setText(
        registering ? "Already have an account? Sign in" : "Don't have an account? Sign up");
    error.setText("");
  }

  private void setBusy(boolean b) {
    busy = b;
    submit.setEnabled(!b);
    switchMode.setEnabled(!b);
    name.setEnabled(!b);
    email.setEnabled(!b);
    password.setEnabled(!b);
    submit.setText(b ? "Please wait…" : registering ? "Create account" : "Sign in");
  }

  private void authenticate() {
    if (busy) return;
    String mail = email.getText().toString().trim(),
        pass = password.getText().toString(),
        display = name.getText().toString().trim();
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
      error.setText("Enter a valid email address.");
      return;
    }
    String problem = registering ? ChatValidation.nameError(display) : null;
    if (problem == null) problem = ChatValidation.passwordError(pass);
    if (problem != null) {
      error.setText(problem);
      return;
    }
    error.setText("");
    setBusy(true);
    if (registering)
      pendingAuth =
          auth.createUserWithEmailAndPassword(mail, pass)
              .continueWithTask(
                  task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return task.getResult()
                        .getUser()
                        .updateProfile(
                            new UserProfileChangeRequest.Builder().setDisplayName(display).build());
                  });
    else pendingAuth = auth.signInWithEmailAndPassword(mail, pass);
    observeAuth();
  }

  private void observeAuth() {
    if (pendingAuth == null) return;
    setBusy(true);
    pendingAuth.addOnCompleteListener(
        this,
        task -> {
          pendingAuth = null;
          if (auth.getCurrentUser() != null) {
            openChat();
            return;
          }
          failed(task.getException());
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (submit != null) observeAuth();
  }

  @Override
  public Object onRetainNonConfigurationInstance() {
    return pendingAuth;
  }

  private void failed(Exception e) {
    setBusy(false);
    String message = "Could not sign in. Check your connection and try again.";
    if (e instanceof FirebaseAuthWeakPasswordException)
      message = "Choose a stronger password (at least 6 characters).";
    else if (e instanceof FirebaseAuthUserCollisionException)
      message = "This email already has an account. Sign in instead.";
    else if (e instanceof FirebaseAuthInvalidCredentialsException
        || e instanceof FirebaseAuthInvalidUserException)
      message = "Email or password is incorrect.";
    else if (e instanceof FirebaseAuthException
        && "ERROR_OPERATION_NOT_ALLOWED".equals(((FirebaseAuthException) e).getErrorCode()))
      message = "Email/password sign-in is not enabled for this app yet.";
    error.setText(message);
  }

  private void openChat() {
    if (navigated) return;
    navigated = true;
    startActivity(new Intent(this, ChatActivity.class));
    finish();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (auth != null && auth.getCurrentUser() != null && !busy) openChat();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putBoolean("registering", registering);
  }
}
