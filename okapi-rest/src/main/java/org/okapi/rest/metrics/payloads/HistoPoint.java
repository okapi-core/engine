/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.payloads;

import lombok.*;

@AllArgsConstructor
@Setter
@Getter
@NoArgsConstructor
@Builder
public final class HistoPoint {
  public enum TEMPORALITY {
    DELTA,
    CUMULATIVE
  }

  long start;
  long end;
  TEMPORALITY temporality;
  float[] buckets;
  Double sum;
  long count;
  // bucketCounts.length = 1 + buckets.length
  long[] bucketCounts;

  public HistoPoint(
      long start, long end, TEMPORALITY temporality, float[] buckets, long[] bucketCounts) {
    this(start, end, temporality, buckets, null, sum(bucketCounts), bucketCounts);
  }

  private static long sum(long[] values) {
    long sum = 0L;
    for (long value : values) sum += value;
    return sum;
  }
}
