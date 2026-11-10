package kz.semey.map;

final class ChatValidation {
  static String messageError(String value) {
    if (value == null || value.trim().isEmpty()) return "Write a message first.";
    return value.trim().length() > 1000 ? "Keep messages under 1,001 characters." : null;
  }

  static String nameError(String value) {
    if (value == null || value.trim().isEmpty()) return "Enter your name.";
    return value.trim().length() > 40 ? "Use a name of up to 40 characters." : null;
  }

  static String passwordError(String value) {
    return value == null || value.length() < 6
        ? "Use at least 6 characters for your password."
        : null;
  }
}
