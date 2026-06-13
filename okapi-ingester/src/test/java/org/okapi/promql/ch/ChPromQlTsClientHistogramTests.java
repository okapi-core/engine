/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.promql.eval.HistogramSeries;

class ChPromQlTsClientHistogramTests {
  @Test
  void explicitHistogramCountsRemainPerBucketWhenServed() {
    var histogram =
        ChPromQlTsClient.nativeCustomHistogram(
            1L, 2L, new double[] {10d, 20d}, new double[] {5d, 7d, 2d}, 42.5d, 14d);

    assertEquals(HistogramSeries.CUSTOM_BUCKET_SCHEMA, histogram.schema());
    assertArrayEquals(new double[] {10d, 20d}, histogram.customValues());
    assertArrayEquals(new double[] {5d, 7d, 2d}, histogram.positiveBuckets());
  }

  @Test
  void cumulativeExplicitHistogramsAreNormalizedToDeltasWhenServed() {
    var first =
        ChPromQlTsClient.nativeCustomHistogram(
            0L, 1L, new double[] {10d}, new double[] {2d, 3d}, 7d, 5d);
    var second =
        ChPromQlTsClient.nativeCustomHistogram(
            1L, 2L, new double[] {10d}, new double[] {5d, 7d}, 17d, 12d);

    var deltas = ChPromQlTsClient.toDeltaHistos(List.of(first, second));

    assertArrayEquals(new double[] {2d, 3d}, deltas.get(0).positiveBuckets());
    assertArrayEquals(new double[] {3d, 4d}, deltas.get(1).positiveBuckets());
    assertEquals(10d, deltas.get(1).sum());
    assertEquals(7d, deltas.get(1).count());
  }
}
