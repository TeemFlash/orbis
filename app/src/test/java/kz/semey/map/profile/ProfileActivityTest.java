package kz.semey.map.profile;

import static org.junit.Assert.*;
import org.junit.Test;

public class ProfileActivityTest {
  @Test public void profileFieldsRequireNameAndRespectLimits() {
    assertNotNull(ProfileActivity.validationError("  ", "bio"));
    assertNull(ProfileActivity.validationError("  Sam  ", "A bio"));
    assertNotNull(ProfileActivity.validationError("x".repeat(61), ""));
    assertNotNull(ProfileActivity.validationError("Sam", "x".repeat(241)));
  }

  @Test public void eachAccountHasIndependentLocalStorageScope() {
    assertEquals("profile_guest", ProfileActivity.profileScope(null));
    assertEquals("profile_guest", ProfileActivity.profileScope(" "));
    assertNotEquals(ProfileActivity.profileScope("account-a"), ProfileActivity.profileScope("account-b"));
    assertEquals(ProfileActivity.profileScope("account-a"), ProfileActivity.profileScope("account-a"));
  }

  @Test public void initialsUseRealDisplayNameWithoutHandle() {
    assertEquals("SG", ProfileActivity.initialsFor("Sam Green"));
    assertEquals("S", ProfileActivity.initialsFor("Sam"));
    assertEquals("?", ProfileActivity.initialsFor(" "));
  }

  @Test public void pickerResultsAreScopedToAnOpenEditorSession() {
    ProfileActivity.EditorEpoch epoch = new ProfileActivity.EditorEpoch();
    long first = epoch.begin();
    epoch.startPicker(first);
    assertTrue(epoch.hasPendingPicker(first));
    assertTrue(epoch.completePicker(first));
    assertFalse(epoch.completePicker(first));
    epoch.invalidate();
    assertFalse(epoch.isActive(first));
    assertFalse(epoch.completePicker(first));
  }

  @Test public void reopeningEditorInvalidatesEarlierPickerRequest() {
    ProfileActivity.EditorEpoch epoch = new ProfileActivity.EditorEpoch();
    long first = epoch.begin();
    epoch.startPicker(first);
    long second = epoch.begin();
    assertFalse(epoch.isActive(first));
    assertFalse(epoch.hasPendingPicker(first));
    assertTrue(epoch.isActive(second));
    assertFalse(epoch.completePicker(first));
  }

  @Test public void allExifOrientationsMapAsymmetricCornersAndSwapDimensionsWhenNeeded() {
    int width = 240;
    int height = 160;
    float[][] expectedTopLeft = {
      {0, 0}, {width, 0}, {width, height}, {0, height},
      {0, 0}, {height, 0}, {height, width}, {0, width}
    };
    for (int orientation = 1; orientation <= 8; orientation++) {
      float[] matrix = ProfileActivity.orientationMatrixValues(orientation, width, height);
      float x = matrix[2];
      float y = matrix[5];
      assertEquals("EXIF " + orientation + " top-left x", expectedTopLeft[orientation - 1][0], x, 0f);
      assertEquals("EXIF " + orientation + " top-left y", expectedTopLeft[orientation - 1][1], y, 0f);
      int outWidth = orientation >= 5 ? height : width;
      int outHeight = orientation >= 5 ? width : height;
      float[][] sourceCorners = {{0, 0}, {width, 0}, {0, height}, {width, height}};
      for (float[] corner : sourceCorners) {
        float mappedX = matrix[0] * corner[0] + matrix[1] * corner[1] + matrix[2];
        float mappedY = matrix[3] * corner[0] + matrix[4] * corner[1] + matrix[5];
        assertTrue("EXIF " + orientation + " x bounds", mappedX >= 0 && mappedX <= outWidth);
        assertTrue("EXIF " + orientation + " y bounds", mappedY >= 0 && mappedY <= outHeight);
      }
    }
    assertArrayEquals(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1},
        ProfileActivity.orientationMatrixValues(0, width, height), 0f);
  }
}
