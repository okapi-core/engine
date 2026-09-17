/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ConversionConfig {
  private final boolean prometheusDialect;

  public static final ConversionConfig DEFAULT = new ConversionConfig(false);
  public static final ConversionConfig PROMETHEUS = new ConversionConfig(true);

  public static ConversionConfig fromHeader(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return DEFAULT;
    }
    var normalized = headerValue.trim().toLowerCase(Locale.ROOT);
    return (normalized.equals("prometheus") ? PROMETHEUS : DEFAULT);
  }

  public static ConversionConfig noOp() {
    return DEFAULT;
  }

  public static ConversionConfig prometheus() {
    return PROMETHEUS;
  }
}
