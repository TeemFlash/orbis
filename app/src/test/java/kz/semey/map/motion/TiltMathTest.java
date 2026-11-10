package kz.semey.map.motion;

import static org.junit.Assert.*;
import org.junit.Test;

public class TiltMathTest {
  private static final float EPSILON = 0.0001f;

  @Test public void neutralReferenceProducesZeroAndOppositeLeansRemainOpposite() {
    TiltMath.Angles neutral = TiltMath.toDisplay(new TiltMath.Angles(0.31f, -0.22f), 0);
    assertAngles(0f, 0f, TiltMath.relative(neutral, neutral));
    TiltMath.Angles right = TiltMath.relative(new TiltMath.Angles(0.2f, 0f), new TiltMath.Angles(0f, 0f));
    TiltMath.Angles left = TiltMath.relative(new TiltMath.Angles(-0.2f, 0f), new TiltMath.Angles(0f, 0f));
    assertEquals(right.horizontal, -left.horizontal, EPSILON);
    TiltMath.Angles up = TiltMath.relative(new TiltMath.Angles(0f, 0.2f), new TiltMath.Angles(0f, 0f));
    TiltMath.Angles down = TiltMath.relative(new TiltMath.Angles(0f, -0.2f), new TiltMath.Angles(0f, 0f));
    assertEquals(up.vertical, -down.vertical, EPSILON);
  }

  @Test public void allDisplayRotationsMapDeviceAxes() {
    TiltMath.Angles raw = new TiltMath.Angles(0.2f, 0.3f);
    assertAngles(0.2f, 0.3f, TiltMath.toDisplay(raw, 0));
    assertAngles(0.3f, -0.2f, TiltMath.toDisplay(raw, 1));
    assertAngles(-0.2f, -0.3f, TiltMath.toDisplay(raw, 2));
    assertAngles(-0.3f, 0.2f, TiltMath.toDisplay(raw, 3));
  }

  @Test public void angleWrapUsesShortestPathAcrossPi() {
    assertEquals(0.02f, TiltMath.wrapDeltaRadians((float) -Math.PI + 0.01f, (float) Math.PI - 0.01f), EPSILON);
  }

  @Test public void offsetsAreBoundedAndNonFiniteValuesAreRejected() {
    assertEquals(TiltMath.MAX_OFFSET_RADIANS, TiltMath.clampOffset(4f), 0f);
    assertEquals(-TiltMath.MAX_OFFSET_RADIANS, TiltMath.clampOffset(-4f), 0f);
    assertEquals(0f, TiltMath.clampOffset(Float.NaN), 0f);
    assertNull(TiltMath.toDisplay(new TiltMath.Angles(Float.POSITIVE_INFINITY, 0f), 0));
    assertNull(TiltMath.quaternionFromRotationVector(new float[] {Float.NaN, 0f, 0f}));
    assertNull(TiltMath.quaternionFromRotationVector(new float[] {0f, 0f, 0f, Float.POSITIVE_INFINITY}));
    assertNull(TiltMath.relativeGravity(new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 0f}));
  }

  @Test public void rotationQuaternionIsNormalizedAndFilterResetCalibratesImmediately() {
    TiltMath.Angles identity = TiltMath.anglesFromQuaternion(TiltMath.quaternionFromRotationVector(new float[] {0f, 0f, 0f, 3f}));
    assertAngles(0f, 0f, identity);
    TiltMath.Filter filter = new TiltMath.Filter();
    assertAngles(0.2f, -0.1f, filter.update(new TiltMath.Angles(0.2f, -0.1f), 1_000_000_000L));
    TiltMath.Angles smoothed = filter.update(new TiltMath.Angles(0.4f, -0.1f), 1_150_000_000L);
    assertTrue(smoothed.horizontal > 0.2f && smoothed.horizontal < 0.4f);
    filter.reset();
    assertAngles(-0.3f, 0.1f, filter.update(new TiltMath.Angles(-0.3f, 0.1f), 7L));
  }

  @Test public void uprightNeutralPoseUsesRelativeQuaternionWithoutEulerFlip() {
    float halfQuarter = (float) Math.sin(Math.PI / 4.0);
    float cosQuarter = (float) Math.cos(Math.PI / 4.0);
    TiltMath.Quaternion neutral = new TiltMath.Quaternion(0f, halfQuarter, 0f, cosQuarter);
    float halfLean = (float) Math.sin(0.05);
    float cosLean = (float) Math.cos(0.05);
    TiltMath.Quaternion leanX = multiply(neutral, new TiltMath.Quaternion(halfLean, 0f, 0f, cosLean));
    TiltMath.Angles relativeX = TiltMath.anglesFromQuaternion(TiltMath.relative(neutral, leanX));
    assertAngles(0f, -0.1f, relativeX);

    TiltMath.Quaternion leanY = multiply(neutral, new TiltMath.Quaternion(0f, halfLean, 0f, cosLean));
    TiltMath.Angles relativeY = TiltMath.anglesFromQuaternion(TiltMath.relative(neutral, leanY));
    assertAngles(0.1f, 0f, relativeY);
  }

  @Test public void gravityFallbackMatchesRotationVectorLeanDirections() {
    float s = (float) Math.sin(0.15), c = (float) Math.cos(0.15);
    assertAngles(0.15f, 0f, TiltMath.relativeGravity(new float[] {0f, 0f, 1f}, new float[] {-s, 0f, c}));
    assertAngles(0f, -0.15f, TiltMath.relativeGravity(new float[] {0f, 0f, 1f}, new float[] {0f, s, c}));
  }

  @Test public void gravityFallbackIsStableAtUprightNeutralAndForTinyLeans() {
    float[] neutral = {0f, 1f, 0f};
    assertAngles(0f, 0f, TiltMath.relativeGravity(neutral, neutral));
    assertAngles(0f, 0f, TiltMath.relativeGravity(neutral, new float[] {0.001f, 1f, 0f}));
    assertAngles(0f, 0f, TiltMath.relativeGravity(neutral, new float[] {-0.001f, 1f, 0f}));
    assertAngles(0f, -0.001f, TiltMath.relativeGravity(neutral, new float[] {0f, 1f, -0.001f}));
    assertAngles(0f, 0.001f, TiltMath.relativeGravity(neutral, new float[] {0f, 1f, 0.001f}));
  }

  @Test public void relativeGravityNormalizesSamplesAndKeepsNeutralAtZero() {
    assertAngles(0f, 0f, TiltMath.relativeGravity(new float[] {0.1f, 0.9f, -0.2f}, new float[] {1f, 9f, -2f}));
    assertAngles(0f, -0.1f, TiltMath.relativeGravity(new float[] {0f, 1f, 0f}, new float[] {0f, (float) Math.cos(0.1), -(float) Math.sin(0.1)}));
    assertAngles(0.1f, 0f, TiltMath.relativeGravity(new float[] {1f, 0f, 0f}, new float[] {(float) Math.cos(0.1), 0f, (float) Math.sin(0.1)}));
  }

  @Test public void relativeGravityHandlesParallelAndAntiparallelVectors() {
    assertAngles(0f, 0f, TiltMath.relativeGravity(new float[] {0f, 9.81f, 0f}, new float[] {0f, 2f, 0f}));
    TiltMath.Angles opposite = TiltMath.relativeGravity(new float[] {0f, 1f, 0f}, new float[] {0f, -1f, 0f});
    assertNotNull(opposite);
    assertFalse(Float.isNaN(opposite.horizontal) || Float.isInfinite(opposite.horizontal));
    assertFalse(Float.isNaN(opposite.vertical) || Float.isInfinite(opposite.vertical));
  }

  private static TiltMath.Quaternion multiply(TiltMath.Quaternion a, TiltMath.Quaternion b) {
    return new TiltMath.Quaternion(
        a.w*b.x + a.x*b.w + a.y*b.z - a.z*b.y,
        a.w*b.y - a.x*b.z + a.y*b.w + a.z*b.x,
        a.w*b.z + a.x*b.y - a.y*b.x + a.z*b.w,
        a.w*b.w - a.x*b.x - a.y*b.y - a.z*b.z);
  }

  private static void assertAngles(float h, float v, TiltMath.Angles actual) {
    assertNotNull(actual);
    assertEquals(h, actual.horizontal, EPSILON);
    assertEquals(v, actual.vertical, EPSILON);
  }
}
