/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
public class ReadonlyHistogram {
  public enum TEMPORALITY {
    DELTA,
    CUMULATIVE
  }

  @Getter long startTs;
  @Getter Long endTs;
  @Getter Histogram.TEMPORALITY temporality;
  long[] bucketCounts;
  float[] buckets;

  public List<Long> getBucketCounts() {
    return new UnmodifiableLongList(bucketCounts);
  }

  public List<Float> getBuckets() {
    return new UnmodifiableDoubleList(buckets);
  }
}
