/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.time;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class PromQlDateParser {

  public static Optional<Long> parseAsUnix(String time) {
    try {
      double asDouble = Double.parseDouble(time);
      return Optional.of((long) (asDouble * 1000));
    } catch (NumberFormatException e) {
      try {
        Instant instant = Instant.parse(time);
        return Optional.of(instant.toEpochMilli());
      } catch (DateTimeParseException dtpe) {
        return Optional.empty();
      }
    }
  }

  public static String now() {
    return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
