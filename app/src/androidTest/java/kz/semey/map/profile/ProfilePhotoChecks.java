package kz.semey.map.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** Runtime bitmap/EXIF checks callable from an authorized instrumentation harness. */
public final class ProfilePhotoChecks {
  private static final int BLUE = Color.rgb(24, 72, 220);
  private static final int RED = Color.rgb(220, 36, 28);
  private static final int YELLOW = Color.rgb(235, 210, 24);
  private static final int GREEN = Color.rgb(26, 190, 54);

  private ProfilePhotoChecks() {}

  public static void run(Context context) throws Exception {
    int[][] expected = {
      {BLUE, RED, YELLOW, GREEN},
      {RED, BLUE, GREEN, YELLOW},
      {GREEN, YELLOW, RED, BLUE},
      {YELLOW, GREEN, BLUE, RED},
      {BLUE, YELLOW, RED, GREEN},
      {YELLOW, BLUE, GREEN, RED},
      {GREEN, RED, YELLOW, BLUE},
      {RED, GREEN, BLUE, YELLOW}
    };
    for (int orientation = 1; orientation <= 8; orientation++) {
      File file = new File(context.getCacheDir(), "orbis-exif-" + orientation + ".jpg");
      Bitmap source = fixture();
      try (FileOutputStream out = new FileOutputStream(file)) {
        if (!source.compress(Bitmap.CompressFormat.JPEG, 100, out)) throw new AssertionError("fixture encode failed");
      } finally {
        source.recycle();
      }
      ExifInterface exif = new ExifInterface(file.getAbsolutePath());
      exif.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(orientation));
      exif.saveAttributes();

      Bitmap normalized;
      try (FileInputStream in = new FileInputStream(file)) {
        normalized = ProfileActivity.decodeAndNormalize(in);
      }
      int expectedWidth = orientation >= 5 ? 160 : 240;
      int expectedHeight = orientation >= 5 ? 240 : 160;
      if (normalized.getWidth() != expectedWidth || normalized.getHeight() != expectedHeight) {
        int actualWidth = normalized.getWidth();
        int actualHeight = normalized.getHeight();
        normalized.recycle();
        throw new AssertionError("EXIF " + orientation + " dimensions were " + actualWidth + "x" + actualHeight);
      }
      int[][] actual = corners(normalized);
      normalized.recycle();
      for (int corner = 0; corner < 4; corner++) {
        if (!near(actual[corner][0], expected[orientation - 1][corner])) {
          throw new AssertionError("EXIF " + orientation + " corner " + corner + " was not normalized");
        }
      }
      file.delete();
    }
  }

  private static Bitmap fixture() {
    Bitmap bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
    for (int y = 0; y < 160; y++) {
      for (int x = 0; x < 240; x++) {
        int color = y < 80 ? (x < 120 ? BLUE : RED) : (x < 120 ? YELLOW : GREEN);
        bitmap.setPixel(x, y, color);
      }
    }
    return bitmap;
  }

  private static int[][] corners(Bitmap bitmap) {
    int xInset = Math.max(8, bitmap.getWidth() / 16);
    int yInset = Math.max(8, bitmap.getHeight() / 16);
    return new int[][] {
      {bitmap.getPixel(xInset, yInset)},
      {bitmap.getPixel(bitmap.getWidth() - 1 - xInset, yInset)},
      {bitmap.getPixel(xInset, bitmap.getHeight() - 1 - yInset)},
      {bitmap.getPixel(bitmap.getWidth() - 1 - xInset, bitmap.getHeight() - 1 - yInset)}
    };
  }

  private static boolean near(int actual, int expected) {
    return Math.abs(Color.red(actual) - Color.red(expected)) < 90
        && Math.abs(Color.green(actual) - Color.green(expected)) < 90
        && Math.abs(Color.blue(actual) - Color.blue(expected)) < 90;
  }
}
