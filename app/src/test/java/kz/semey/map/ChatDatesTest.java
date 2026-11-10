package kz.semey.map;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import org.junit.Test;

public class ChatDatesTest {
  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final Locale EN = Locale.US;

  private long at(String instant) {
    return Instant.parse(instant).toEpochMilli();
  }

  @Test
  public void insertsHeaderAtLocalMidnightAndUsesNearestValidMessage() {
    long before = at("2026-11-10T04:59:00Z");
    long after = at("2026-11-10T05:01:00Z");
    ZoneId newYork = ZoneId.of("America/New_York");
    assertEquals("9 Nov 2026", ChatDates.separatorLabel(Arrays.asList(before, null, after), 0, newYork, EN));
    assertNull(ChatDates.separatorLabel(Arrays.asList(before, null, after), 1, newYork, EN));
    assertEquals("10 Nov 2026", ChatDates.separatorLabel(Arrays.asList(before, null, after), 2, newYork, EN));
  }

  @Test
  public void detectsMonthAndYearBoundaries() {
    long nov = at("2026-11-01T00:00:00Z");
    long dec = at("2026-12-01T00:00:00Z");
    long jan = at("2027-01-01T00:00:00Z");
    assertEquals("1 Nov 2026", ChatDates.separatorLabel(Collections.singletonList(nov), 0, UTC, EN));
    assertEquals("1 Dec 2026", ChatDates.separatorLabel(Arrays.asList(nov, dec), 1, UTC, EN));
    assertEquals("1 Jan 2027", ChatDates.separatorLabel(Arrays.asList(dec, jan), 1, UTC, EN));
  }

  @Test
  public void localCalendarDayDependsOnDeviceZone() {
    long stamp = at("2026-11-09T20:00:00Z");
    assertEquals("2026-11-09", ChatDates.dateKey(stamp, UTC));
    assertEquals("2026-11-10", ChatDates.dateKey(stamp, ZoneId.of("Asia/Almaty")));
  }

  @Test
  public void sameLocalDayKeepsOneGroupAndShowsLocalTime() {
    ZoneId newYork = ZoneId.of("America/New_York");
    long first = at("2026-11-10T05:10:00Z");
    long second = at("2026-11-10T05:59:00Z");
    assertEquals("10 Nov 2026", ChatDates.separatorLabel(Collections.singletonList(first), 0, newYork, EN));
    assertNull(ChatDates.separatorLabel(Arrays.asList(first, second), 1, newYork, EN));
    assertEquals("00:59", ChatDates.timeLabel(second, false, newYork, EN));
  }

  @Test
  public void unknownOrPendingTimesAreNeverInvented() {
    assertFalse(ChatDates.isValid(null));
    assertFalse(ChatDates.isValid(0L));
    assertFalse(ChatDates.isValid(-1L));
    assertNull(ChatDates.separatorLabel(Arrays.asList(100L, null), 1, UTC, EN));
    assertEquals("Time unavailable", ChatDates.timeLabel(null, false, UTC, EN));
    assertEquals("Time unavailable", ChatDates.timeLabel(0L, false, UTC, EN));
    assertEquals("Sending…", ChatDates.timeLabel(null, true, UTC, EN));
  }
}
