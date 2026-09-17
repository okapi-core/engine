/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.clock;

public class OkapiTimeUtils {
  public static long nanosToMillis(long nanos) {
    return nanos / 1_000_000;
  }
}
