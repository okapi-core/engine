/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.timeutils;

public class TimeUtils {
  public static long millisToNanos(long millis) {
    return millis * 1_000_000L;
  }

  public static long roundToNearestHour(long millis) {
    return millis - (millis % 3_600_000L);
  }
}
