/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval;

import java.util.ArrayList;
import java.util.Collections;
import org.okapi.metrics.pojos.results.GaugeScan;

public final class Staleness {
  // Distinguish stale markers from regular NaN values.
  private static final int STALE_BITS = 0x7fc00001;

  private Staleness() {}

  public static float staleFloat() {
    return Float.intBitsToFloat(STALE_BITS);
  }

  public static boolean isStale(float value) {
    return Float.isNaN(value) && Float.floatToRawIntBits(value) == STALE_BITS;
  }

  public static GaugeScan withoutStaleSamples(GaugeScan scan) {
    var timestamps = scan.getTimestamps();
    var values = scan.getValues();
    ListBuilder filtered = null;
    for (int i = 0; i < values.size(); i++) {
      if (isStale(values.get(i))) {
        if (filtered == null) {
          filtered = new ListBuilder(timestamps.size() - 1);
          filtered.addAll(timestamps, values, i);
        }
      } else if (filtered != null) {
        filtered.add(timestamps.get(i), values.get(i));
      }
    }
    if (filtered == null) return scan;
    return GaugeScan.builder()
        .universalPath(scan.getUniversalPath())
        .timestamps(Collections.unmodifiableList(filtered.timestamps))
        .values(Collections.unmodifiableList(filtered.values))
        .build();
  }

  private static final class ListBuilder {
    private final ArrayList<Long> timestamps;
    private final ArrayList<Float> values;

    private ListBuilder(int capacity) {
      timestamps = new ArrayList<>(capacity);
      values = new ArrayList<>(capacity);
    }

    private void addAll(
        java.util.List<Long> sourceTimestamps, java.util.List<Float> sourceValues, int end) {
      for (int i = 0; i < end; i++) add(sourceTimestamps.get(i), sourceValues.get(i));
    }

    private void add(long timestamp, float value) {
      timestamps.add(timestamp);
      values.add(value);
    }
  }
}
