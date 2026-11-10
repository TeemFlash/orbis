package kz.semey.map;

import android.content.ContentResolver;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import java.io.*;

/** Bounded JPEG conversion. Re-encoding removes camera/location metadata. */
final class PhotoCodec {
  static final int MAX_BYTES = 100 * 1024;

  static String encode(ContentResolver resolver, Uri uri) throws IOException {
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    try (InputStream in = resolver.openInputStream(uri)) {
      BitmapFactory.decodeStream(in, null, o);
    }
    if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("Unsupported image");
    o.inSampleSize = 1;
    while (Math.max(o.outWidth, o.outHeight) / o.inSampleSize > 1600) o.inSampleSize *= 2;
    o.inJustDecodeBounds = false;
    Bitmap bitmap;
    try (InputStream in = resolver.openInputStream(uri)) {
      bitmap = BitmapFactory.decodeStream(in, null, o);
    }
    if (bitmap == null) throw new IOException("Could not decode image");
    try {
      int orientation = 1;
      try (InputStream in = resolver.openInputStream(uri)) {
        orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
      } catch (IOException ignored) {
      }
      Matrix matrix = new Matrix();
      switch (orientation) {
        case 2:
          matrix.setScale(-1, 1);
          break;
        case 3:
          matrix.setRotate(180);
          break;
        case 4:
          matrix.setScale(1, -1);
          break;
        case 5:
          matrix.setRotate(90);
          matrix.postScale(-1, 1);
          break;
        case 6:
          matrix.setRotate(90);
          break;
        case 7:
          matrix.setRotate(-90);
          matrix.postScale(-1, 1);
          break;
        case 8:
          matrix.setRotate(-90);
          break;
      }
      if (!matrix.isIdentity()) {
        Bitmap rotated =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) {
          bitmap.recycle();
          bitmap = rotated;
        }
      }
      float ratio = Math.min(1f, 960f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
      if (ratio < 1) {
        Bitmap scaled =
            Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                Math.max(1, Math.round(bitmap.getHeight() * ratio)),
                true);
        bitmap.recycle();
        bitmap = scaled;
      }
      for (int attempt = 0; attempt < 8; attempt++) {
        for (int quality = 85; quality >= 40; quality -= 15) {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
          if (out.size() <= MAX_BYTES)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }
        Bitmap scaled =
            Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, bitmap.getWidth() * 3 / 4),
                Math.max(1, bitmap.getHeight() * 3 / 4),
                true);
        bitmap.recycle();
        bitmap = scaled;
      }
      throw new IOException("Image is too large");
    } finally {
      bitmap.recycle();
    }
  }

  static Bitmap decode(String encoded) {
    if (encoded == null || encoded.length() > 136536) return null;
    try {
      byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
      if (bytes.length > MAX_BYTES) return null;
      BitmapFactory.Options o = new BitmapFactory.Options();
      o.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
      if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 1600 || o.outHeight > 1600)
        return null;
      o.inSampleSize=1;
      while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>512)o.inSampleSize*=2;
      o.inJustDecodeBounds = false;
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
