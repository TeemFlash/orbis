package kz.semey.map;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Pure local-time formatting and grouping for timestamps already supplied by chat messages. */
public final class ChatDates {
  private ChatDates() {}

  public static boolean isValid(Long timestamp) {
    return timestamp != null && timestamp > 0;
  }

  public static String dateKey(long timestamp, ZoneId zone) {
    return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().toString();
  }

  public static String dateLabel(long timestamp, ZoneId zone, Locale locale) {
    return DateTimeFormatter.ofPattern("d MMM uuuu", locale)
        .format(Instant.ofEpochMilli(timestamp).atZone(zone));
  }

  public static String timeLabel(Long timestamp, boolean pending, ZoneId zone, Locale locale) {
    if (pending) return "Sending…";
    if (!isValid(timestamp)) return "Time unavailable";
    return DateTimeFormatter.ofPattern("HH:mm", locale)
        .format(Instant.ofEpochMilli(timestamp).atZone(zone));
  }

  /** Returns a date header when this message begins a new local day, or null for no header. */
  public static String separatorLabel(
      List<Long> timestamps, int position, ZoneId zone, Locale locale) {
    if (position < 0 || position >= timestamps.size()) return null;
    Long current = timestamps.get(position);
    if (!isValid(current)) return null;
    String currentKey = dateKey(current, zone);
    for (int i = position - 1; i >= 0; i--) {
      Long previous = timestamps.get(i);
      if (isValid(previous))
        return currentKey.equals(dateKey(previous, zone)) ? null : dateLabel(current, zone, locale);
    }
    return dateLabel(current, zone, locale);
  }
}
