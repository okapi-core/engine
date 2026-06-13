/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

public final class Staleness {
  // Distinguish stale markers from regular NaN values.
  private static final int STALE_BITS = 0x7fc00001;

  private Staleness() {}

  public static float staleFloat() {
    return Float.intBitsToFloat(STALE_BITS);
  }

  public static boolean isStale(float value) {
    return Float.isNaN(value) && Float.floatToIntBits(value) == STALE_BITS;
  }
}
