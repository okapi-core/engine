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
  // bucketCounts.length = 1 + buckets.length
  long[] bucketCounts;
}
