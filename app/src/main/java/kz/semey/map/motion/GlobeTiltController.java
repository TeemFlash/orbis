package kz.semey.map.motion;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.view.WindowManager;

/** Foreground-only sensor bridge. The owner decides whether the Globe is visible. */
public final class GlobeTiltController implements SensorEventListener {
  public interface Sink { void onTilt(float horizontalRadians, float verticalRadians); }
  private static final long MIN_EMIT_INTERVAL_MS = 34L;
  private final SensorManager manager;
  private final WindowManager windowManager;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final Sink sink;
  private final TiltMath.Filter filter = new TiltMath.Filter();
  private Sensor sensor;
  private boolean running;
  private float[] neutralGravity;
  private TiltMath.Quaternion neutralRotation;
  private TiltMath.Angles latest = new TiltMath.Angles(0f, 0f);
  private int displayRotation;
  private long lastEmitMillis;
  private boolean pending;
  private final Runnable emitLatest = () -> {
    synchronized (GlobeTiltController.this) {
      pending = false;
      if (!running) return;
      dispatch(latest);
    }
  };

  public GlobeTiltController(Context context, Sink sink) {
    Context application = context.getApplicationContext();
    manager = (SensorManager) application.getSystemService(Context.SENSOR_SERVICE);
    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    this.sink = sink;
    displayRotation = currentRotation();
  }

  public synchronized void start() {
    if (running || manager == null) return;
    neutralGravity = null;
    neutralRotation = null;
    latest = new TiltMath.Angles(0f, 0f);
    filter.reset();
    displayRotation = currentRotation();
    Sensor[] candidates = {
      manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
      manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
      manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    };
    for (Sensor candidate : candidates) {
      if (candidate == null) continue;
      boolean registered;
      try { registered = manager.registerListener(this, candidate, SensorManager.SENSOR_DELAY_UI); }
      catch (RuntimeException error) { registered = false; }
      if (registered) {
        sensor = candidate;
        running = true;
        lastEmitMillis = 0L;
        return;
      }
    }
    sensor = null;
    // No motion hardware: the globe's existing touch controls remain available.
  }

  public synchronized void stop() {
    ui.removeCallbacks(emitLatest);
    pending = false;
    if (running) manager.unregisterListener(this);
    running = false;
    sensor = null;
    neutralGravity = null;
    neutralRotation = null;
    latest = new TiltMath.Angles(0f, 0f);
    filter.reset();
    lastEmitMillis = 0L;
    sink.onTilt(0f, 0f);
  }

  public synchronized boolean isRunning() { return running; }

  @Override public synchronized void onSensorChanged(SensorEvent event) {
    if (!running || event.sensor != sensor) return;
    boolean gravity = sensor.getType() == Sensor.TYPE_GRAVITY;
    TiltMath.Quaternion quaternion = gravity ? null : TiltMath.quaternionFromRotationVector(event.values);
    TiltMath.Angles gravityDelta =
        gravity
            ? TiltMath.relativeGravity(
                neutralGravity == null ? event.values : neutralGravity, event.values)
            : null;
    if (gravity ? gravityDelta == null : quaternion == null) return;

    int rotation = currentRotation();
    if (rotation != displayRotation) {
      displayRotation = rotation;
      neutralGravity = null;
      neutralRotation = null;
      filter.reset();
      latest = new TiltMath.Angles(0f, 0f);
    }
    TiltMath.Angles delta;
    if (gravity) {
      if (neutralGravity == null) {
        neutralGravity = event.values.clone();
        latest = new TiltMath.Angles(0f, 0f);
        filter.reset();
        emitCoalesced();
        return;
      }
      delta = TiltMath.toDisplay(gravityDelta, rotation);
    } else {
      if (neutralRotation == null) {
        neutralRotation = quaternion;
        latest = new TiltMath.Angles(0f, 0f);
        filter.reset();
        emitCoalesced();
        return;
      }
      TiltMath.Quaternion relative = TiltMath.relative(neutralRotation, quaternion);
      delta = TiltMath.toDisplay(TiltMath.anglesFromQuaternion(relative), rotation);
    }
    if (delta == null) return;
    latest = filter.update(delta, event.timestamp);
    emitCoalesced();
  }

  private void emitCoalesced() {
    long now = SystemClock.uptimeMillis();
    long elapsed = now - lastEmitMillis;
    if (lastEmitMillis == 0L || elapsed >= MIN_EMIT_INTERVAL_MS) {
      ui.removeCallbacks(emitLatest);
      pending = false;
      dispatch(latest);
    } else if (!pending) {
      pending = true;
      ui.postDelayed(emitLatest, MIN_EMIT_INTERVAL_MS - elapsed);
    }
  }

  private void dispatch(TiltMath.Angles value) {
    lastEmitMillis = SystemClock.uptimeMillis();
    sink.onTilt(TiltMath.clampOffset(value.horizontal), TiltMath.clampOffset(value.vertical));
  }

  private int currentRotation() {
    return windowManager == null || windowManager.getDefaultDisplay() == null
        ? Surface.ROTATION_0 : windowManager.getDefaultDisplay().getRotation();
  }

  @Override public void onAccuracyChanged(Sensor ignored, int accuracy) {}
}
