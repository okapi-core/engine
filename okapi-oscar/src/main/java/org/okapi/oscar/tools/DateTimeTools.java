/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class DateTimeTools {

  @Tool(
      description =
          "Returns the current time in MILLISECONDS since Unix epoch."
              + " Use the result for fields named tsStartMillis, tsEndMillis, startMs, endMs, or"
              + " any field that expects millisecond timestamps.")
  public long currentTime() {
    return System.currentTimeMillis();
  }

  @Tool(
      description =
          "Returns the current time in NANOSECONDS since Unix epoch."
              + " Use the result for fields named tsStartNanos, tsEndNanos, startNanos, endNanos,"
              + " or any field that expects nanosecond timestamps.")
  public long currentTimeNanos() {
    Instant now = Instant.now();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  @Tool(
      description =
          "Returns a time range ending at now and spanning the given duration."
              + " Output fields startMs and endMs are in MILLISECONDS since Unix epoch."
              + " Use for fields named tsStartMillis, tsEndMillis, startMs, endMs, or any field"
              + " that expects millisecond timestamps."
              + " Example: timeRange(1, HOURS) gives the last 1 hour as millisecond timestamps.")
  public TimeRange timeRange(
      @ToolParam(description = "Duration value, e.g. 1 for 1 hour or 30 for 30 minutes.")
          int duration,
      @ToolParam(
              description =
                  "Time unit for the duration. Valid values: SECONDS, MINUTES, HOURS, DAYS.")
          TimeUnit unit) {
    long endMs = System.currentTimeMillis();
    long startMs = endMs - unit.toMillis(duration);
    return TimeRange.builder().startMs(startMs).endMs(endMs).build();
  }

  @Tool(
      description =
"""
Returns a time range ending at now and spanning the given duration.
Output fields startNanos and endNanos are in NANOSECONDS since Unix epoch.
Use for fields named tsStartNanos, tsEndNanos, startNanos, endNanos, or any
field that expects nanosecond timestamps. Do NOT use for millisecond fields.
Example: timeRangeNanos(1, HOURS) gives the last 1 hour as nanosecond
timestamps, suitable for trace queries.
""")
  public TimeRangeNanos timeRangeNanos(
      @ToolParam(description = "Duration value, e.g. 1 for 1 hour or 30 for 30 minutes.")
          int duration,
      @ToolParam(
              description =
                  "Time unit for the duration. Valid values: SECONDS, MINUTES, HOURS, DAYS.")
          TimeUnit unit) {
    Instant now = Instant.now();
    long endNanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();
    long startNanos = endNanos - unit.toNanos(duration);
    return TimeRangeNanos.builder().startNanos(startNanos).endNanos(endNanos).build();
  }

  @Tool(
      description =
"""
Purpose
Convert a Unix epoch timestamp in SECONDS to an ISO-8601 timestamp string.
Hint
Use this when timestamps are being returned in a response sent to the user.
Example
linuxEpochToHumanReadableTimestamp(1700000000) -> "2023-11-14T22:13:20Z"
""")
  public String linuxEpochToHumanReadableTimestamp(
      @ToolParam(description = "Unix epoch timestamp in SECONDS.") long epochSeconds) {
    return Instant.ofEpochSecond(epochSeconds).toString();
  }

  @Builder
  @Getter
  public static class TimeRange {
    long startMs;
    long endMs;
  }

  @Builder
  @Getter
  public static class TimeRangeNanos {
    long startNanos;
    long endNanos;
  }
}
