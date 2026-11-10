package kz.semey.map.motion;

/** Pure angle and smoothing helpers for the Globe phone-tilt controller. */
public final class TiltMath {
  public static final float MAX_OFFSET_RADIANS = 0.45f;
  private static final double TAU_SECONDS = 0.15;

  private TiltMath() {}

  public static final class Angles {
    public final float horizontal;
    public final float vertical;

    public Angles(float horizontal, float vertical) {
      this.horizontal = horizontal;
      this.vertical = vertical;
    }
  }

  public static final class Quaternion {
    public final float x, y, z, w;
    public Quaternion(float x, float y, float z, float w) {
      this.x = x; this.y = y; this.z = z; this.w = w;
    }
  }

  /** Convert and normalize an Android rotation vector into x/y/z/w form. */
  public static Quaternion quaternionFromRotationVector(float[] values) {
    if (values == null || values.length < 3) return null;
    float x = values[0], y = values[1], z = values[2];
    if (!finite(x) || !finite(y) || !finite(z)) return null;
    float w;
    if (values.length >= 4) {
      if (!finite(values[3])) return null;
      w = values[3];
    } else {
      double remainder = 1.0 - (double) x * x - (double) y * y - (double) z * z;
      w = (float) Math.sqrt(Math.max(0.0, remainder));
    }
    double norm = Math.sqrt((double) x * x + (double) y * y + (double) z * z + (double) w * w);
    if (!Double.isFinite(norm) || norm < 1e-8) return null;
    x = (float) (x / norm);
    y = (float) (y / norm);
    z = (float) (z / norm);
    w = (float) (w / norm);
    return new Quaternion(x, y, z, w);
  }

  /** Relative rotation expressed against the neutral device pose. */
  public static Quaternion relative(Quaternion neutral, Quaternion current) {
    if (neutral == null || current == null) return null;
    // inverse(neutral) * current, normalized to suppress accumulated sensor noise.
    float ix = -neutral.x, iy = -neutral.y, iz = -neutral.z, iw = neutral.w;
    float x = iw * current.x + ix * current.w + iy * current.z - iz * current.y;
    float y = iw * current.y - ix * current.z + iy * current.w + iz * current.x;
    float z = iw * current.z + ix * current.y - iy * current.x + iz * current.w;
    float w = iw * current.w - ix * current.x - iy * current.y - iz * current.z;
    double norm = Math.sqrt((double)x*x + (double)y*y + (double)z*z + (double)w*w);
    if (!Double.isFinite(norm) || norm < 1e-8) return null;
    return new Quaternion((float)(x/norm), (float)(y/norm), (float)(z/norm), (float)(w/norm));
  }

  /** Extract the small relative pitch/roll angles from a normalized quaternion. */
  public static Angles anglesFromQuaternion(Quaternion q) {
    if (q == null || !finite(q.x) || !finite(q.y) || !finite(q.z) || !finite(q.w)) return null;
    float r6 = 2f * (q.x * q.z - q.y * q.w);
    float r7 = 2f * (q.y * q.z + q.x * q.w);
    float r8 = 1f - 2f * (q.x * q.x + q.y * q.y);
    return new Angles((float) Math.atan2(-r6, r8), (float) Math.asin(clamp(-r7, -1f, 1f)));
  }

  /** Device rotation implied by the change in normalized gravity direction. */
  public static Angles relativeGravity(float[] neutralValues, float[] currentValues) {
    double[] neutral = normalizedGravity(neutralValues);
    double[] current = normalizedGravity(currentValues);
    if (neutral == null || current == null) return null;
    Quaternion rotation = rotationBetween(current, neutral);
    return anglesFromQuaternion(rotation);
  }

  private static double[] normalizedGravity(float[] values) {
    if (values == null || values.length < 3) return null;
    float x = values[0], y = values[1], z = values[2];
    if (!finite(x) || !finite(y) || !finite(z)) return null;
    double norm = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    if (!Double.isFinite(norm) || norm < 1e-4) return null;
    return new double[] {x / norm, y / norm, z / norm};
  }

  private static Quaternion rotationBetween(double[] from, double[] to) {
    double dot = Math.max(-1.0, Math.min(1.0, from[0] * to[0] + from[1] * to[1] + from[2] * to[2]));
    if (dot > 1.0 - 1e-8) return new Quaternion(0f, 0f, 0f, 1f);

    double x, y, z, w;
    if (dot < -1.0 + 1e-8) {
      // Antiparallel vectors have no unique shortest axis; choose a stable perpendicular one.
      double ax = Math.abs(from[0]), ay = Math.abs(from[1]), az = Math.abs(from[2]);
      double bx = ax <= ay && ax <= az ? 1.0 : 0.0;
      double by = bx == 0.0 && ay <= az ? 1.0 : 0.0;
      double bz = bx == 0.0 && by == 0.0 ? 1.0 : 0.0;
      x = from[1] * bz - from[2] * by;
      y = from[2] * bx - from[0] * bz;
      z = from[0] * by - from[1] * bx;
      double norm = Math.sqrt(x * x + y * y + z * z);
      x /= norm;
      y /= norm;
      z /= norm;
      w = 0.0;
    } else {
      x = from[1] * to[2] - from[2] * to[1];
      y = from[2] * to[0] - from[0] * to[2];
      z = from[0] * to[1] - from[1] * to[0];
      w = 1.0 + dot;
      double norm = Math.sqrt(x * x + y * y + z * z + w * w);
      x /= norm;
      y /= norm;
      z /= norm;
      w /= norm;
    }
    return new Quaternion((float) x, (float) y, (float) z, (float) w);
  }

  /** Remap device roll/pitch into current display-horizontal and display-vertical axes. */
  public static Angles toDisplay(Angles device, int rotation) {
    if (device == null || !finite(device.horizontal) || !finite(device.vertical)) return null;
    switch (rotation) {
      case 1: return new Angles(device.vertical, -device.horizontal);
      case 2: return new Angles(-device.horizontal, -device.vertical);
      case 3: return new Angles(-device.vertical, device.horizontal);
      default: return device;
    }
  }

  public static Angles relative(Angles current, Angles neutral) {
    if (current == null || neutral == null) return null;
    return new Angles(wrapDeltaRadians(current.horizontal, neutral.horizontal),
        wrapDeltaRadians(current.vertical, neutral.vertical));
  }

  public static float wrapDeltaRadians(float current, float neutral) {
    if (!finite(current) || !finite(neutral)) return 0f;
    float delta = current - neutral;
    return (float) Math.atan2(Math.sin(delta), Math.cos(delta));
  }

  public static float clampOffset(float value) {
    return finite(value) ? clamp(value, -MAX_OFFSET_RADIANS, MAX_OFFSET_RADIANS) : 0f;
  }

  private static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  private static boolean finite(float value) {
    return !Float.isNaN(value) && !Float.isInfinite(value);
  }

  /** Timestamp-aware low-pass filter. Reset on every monitor restart. */
  public static final class Filter {
    private boolean initialized;
    private float horizontal, vertical;
    private long timestampNanos;

    public Angles update(Angles sample, long timestamp) {
      if (sample == null || !finite(sample.horizontal) || !finite(sample.vertical))
        return initialized ? new Angles(horizontal, vertical) : new Angles(0f, 0f);
      if (!initialized || timestamp <= timestampNanos) {
        initialized = true;
        horizontal = clampOffset(sample.horizontal);
        vertical = clampOffset(sample.vertical);
      } else {
        double dt = (timestamp - timestampNanos) / 1_000_000_000.0;
        float alpha = (float) (1.0 - Math.exp(-dt / TAU_SECONDS));
        horizontal = clampOffset(horizontal + alpha * (sample.horizontal - horizontal));
        vertical = clampOffset(vertical + alpha * (sample.vertical - vertical));
      }
      timestampNanos = timestamp;
      return new Angles(horizontal, vertical);
    }

    public void reset() {
      initialized = false;
      horizontal = vertical = 0f;
      timestampNanos = 0L;
    }
  }
}
