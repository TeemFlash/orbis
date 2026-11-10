package kz.semey.map;

public class ChatValidationTest {
  public static void main(String[] args) {
    check(ChatValidation.messageError("   ") != null, "blank messages rejected");
    check(ChatValidation.messageError("x".repeat(1001)) != null, "oversized messages rejected");
    check(ChatValidation.messageError("Hello Семей 👋") == null, "Unicode messages accepted");
    check(ChatValidation.messageError("x".repeat(1000)) == null, "boundary accepted");
    check(ChatValidation.nameError(" ") != null, "empty name rejected");
    check(ChatValidation.nameError("x".repeat(41)) != null, "long name rejected");
    check(ChatValidation.nameError("Sam") == null, "normal name accepted");
    check(ChatValidation.passwordError("12345") != null, "short password rejected");
    check(ChatValidation.passwordError("abcdef") == null, "six characters accepted");
    System.out.println("All 9 validation checks passed");
  }
  private static void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
  }
}
