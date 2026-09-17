/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.visitor;

// parse/DurationUtil.java
public final class DurationUtil {
  private DurationUtil() {}

  /**
   * Prom-like duration: 5s, 1m, 2h, 7d, 2w, 1y Also accepts plain numbers (e.g., "15" or "0.5")
   * meaning seconds (Grafana step).
   */
  public static long parseToMillis(String dur) {
    if (dur == null) throw new IllegalArgumentException("Bad duration: null");
    String s = dur.trim();
    if (s.isEmpty()) throw new IllegalArgumentException("Bad duration: \"\"");

    // If it's purely a number (integer or decimal), interpret as seconds.
    boolean numericOrDecimal = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!(Character.isDigit(c) || c == '.')) {
        numericOrDecimal = false;
        break;
      }
    }
    if (numericOrDecimal) {
      // Supports "15", "0.5", "1.25" -> seconds
      java.math.BigDecimal seconds = new java.math.BigDecimal(s);
      return seconds
          .multiply(java.math.BigDecimal.valueOf(1000))
          .setScale(0, java.math.RoundingMode.HALF_UP)
          .longValueExact();
    }

    long totalMs = 0L;
    int i = 0;
    while (i < s.length()) {
      int start = i;
      while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
        i++;
      }
      if (start == i) {
        throw new IllegalArgumentException("Bad duration: " + dur);
      }
      double value = Double.parseDouble(s.substring(start, i));
      if (i >= s.length()) {
        throw new IllegalArgumentException("Bad duration: " + dur);
      }
      if (s.startsWith("ms", i)) {
        totalMs += Math.round(value);
        i += 2;
        continue;
      }
      char unit = Character.toLowerCase(s.charAt(i++));
      totalMs += Math.round(value * unitMultiplier(unit));
    }
    return totalMs;
  }

  private static long unitMultiplier(char unit) {
    return switch (unit) {
      case 's' -> 1000L;
      case 'm' -> 60_000L;
      case 'h' -> 3_600_000L;
      case 'd' -> 86_400_000L;
      case 'w' -> 7L * 86_400_000L;
      case 'y' -> 365L * 86_400_000L;
      default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
    };
  }
}
