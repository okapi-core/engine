/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import org.okapi.exceptions.BadRequestException;
import org.okapi.promql.eval.visitor.DurationUtil;
import org.okapi.promql.time.PromQlDateParser;

public final class PromQlApiTimeParams {

  private PromQlApiTimeParams() {}

  public static long requiredTime(String value, String paramName) throws BadRequestException {
    var parsed = PromQlDateParser.parseAsUnix(value);
    if (parsed.isEmpty()) {
      throw new BadRequestException(String.format("Date: %s is not a valid %s", value, paramName));
    }
    return parsed.get();
  }

  public static long optionalTime(String value, long defaultValue) throws BadRequestException {
    if (value == null) {
      return defaultValue;
    }
    var parsed = PromQlDateParser.parseAsUnix(value);
    if (parsed.isEmpty()) {
      throw new BadRequestException(String.format("Got illegal date %s.", value));
    }
    return parsed.get();
  }

  public static long requiredStepMillis(String value) throws BadRequestException {
    try {
      return DurationUtil.parseToMillis(value);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Date: %s is not a valid step", value));
    }
  }

  public static PromQlTimeRange optionalRange(String start, String end) throws BadRequestException {
    return new PromQlTimeRange(
        optionalTime(start, 0L), optionalTime(end, System.currentTimeMillis()));
  }
}
