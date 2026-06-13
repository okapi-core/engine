/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.payloads;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
public class ExponentialHistoPoint {
  long start;
  long end;
  HistoPoint.TEMPORALITY temporality;
  int scale;
  double zeroThreshold;
  long zeroCount;
  int positiveOffset;
  long[] positiveCounts;
  int negativeOffset;
  long[] negativeCounts;
  Double sum;
  long count;
}
