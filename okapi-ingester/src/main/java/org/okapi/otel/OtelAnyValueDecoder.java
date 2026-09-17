/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.otel;

import io.opentelemetry.proto.common.v1.AnyValue;
import java.util.Locale;
import java.util.Optional;

public final class OtelAnyValueDecoder {
  private OtelAnyValueDecoder() {}

  public static Optional<String> decodeAsString(AnyValue v) {
    if (v == null) return Optional.empty();
    return switch (v.getValueCase()) {
      case STRING_VALUE -> Optional.of(v.getStringValue());
      default -> Optional.empty();
    };
  }

  public static Optional<Integer> decodeAsInt(AnyValue v) {
    if (v == null) return Optional.empty();
    return switch (v.getValueCase()) {
      case INT_VALUE -> Optional.of(clampToInt(v.getIntValue()));
      case DOUBLE_VALUE -> Optional.of(clampToInt(Math.round(v.getDoubleValue())));
      case STRING_VALUE -> parseInt(v.getStringValue());
      default -> Optional.empty();
    };
  }

  public static String bytesToHex(byte[] bytes) {
    if (bytes == null || bytes.length == 0) return "";
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) sb.append(String.format(Locale.ROOT, "%02x", value));
    return sb.toString();
  }

  private static Optional<Integer> parseInt(String value) {
    if (value == null || value.isEmpty()) return Optional.empty();
    try {
      return Optional.of(clampToInt(Long.parseLong(value)));
    } catch (NumberFormatException ignored) {
      try {
        return Optional.of(clampToInt(Math.round(Double.parseDouble(value))));
      } catch (NumberFormatException ignoredAgain) {
        return Optional.empty();
      }
    }
  }

  private static int clampToInt(long value) {
    if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
    return (int) value;
  }
}
