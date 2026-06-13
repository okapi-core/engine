/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class DurationParser {
  private DurationParser() {}

  static long toMillis(String duration) {
    String s = duration.trim();
    if (s.isEmpty()) throw new IllegalArgumentException("invalid duration: " + duration);

    // Plain numeric string → treat as seconds
    boolean allNumeric = true;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isDigit(c) && c != '.') { allNumeric = false; break; }
    }
    if (allNumeric) {
      return new BigDecimal(s)
          .multiply(BigDecimal.valueOf(1000))
          .setScale(0, RoundingMode.HALF_UP)
          .longValueExact();
    }

    long total = 0L;
    int i = 0;
    while (i < s.length()) {
      int start = i;
      while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
      if (start == i) throw new IllegalArgumentException("invalid duration: " + duration);
      double value = Double.parseDouble(s.substring(start, i));
      if (i >= s.length()) throw new IllegalArgumentException("invalid duration: " + duration);
      if (s.startsWith("ms", i)) { total += Math.round(value); i += 2; continue; }
      char unit = s.charAt(i++);
      total += Math.round(value * unitMultiplier(unit));
    }
    return total;
  }

  private static long unitMultiplier(char unit) {
    return switch (unit) {
      case 's' -> 1_000L;
      case 'm' -> 60_000L;
      case 'h' -> 3_600_000L;
      case 'd' -> 86_400_000L;
      case 'w' -> 604_800_000L;
      case 'y' -> 31_536_000_000L;
      default -> throw new IllegalArgumentException("unsupported duration unit: " + unit);
    };
  }
}
