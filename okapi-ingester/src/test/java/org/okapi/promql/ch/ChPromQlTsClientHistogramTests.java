/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  @Test
  void exponentialHistogramsBecomeNativePromQlSamples() {
    var histogram =
        ChPromQlTsClient.nativeExponentialHistogram(
            1L, 2L, 3, 0.01d, 2d, -1, new double[] {3d, 4d}, 2, new double[] {5d}, 12.5d, 14d);

    assertEquals(3, histogram.schema());
    assertEquals(0.01d, histogram.zeroThreshold());
    assertEquals(2d, histogram.zeroCount());
    assertEquals(-1, histogram.positiveOffset());
    assertArrayEquals(new double[] {3d, 4d}, histogram.positiveBuckets());
    assertEquals(2, histogram.negativeOffset());
    assertArrayEquals(new double[] {5d}, histogram.negativeBuckets());
  }

  @Test
  void mixedHistogramRepresentationsAreRejected() {
    var explicit =
        ChPromQlTsClient.nativeCustomHistogram(
            1L, 2L, new double[] {10d}, new double[] {2d, 3d}, 7d, 5d);
    var exponential =
        ChPromQlTsClient.nativeExponentialHistogram(
            1L, 2L, 3, 0.01d, 0d, 0, new double[] {5d}, 0, new double[0], 7d, 5d);

    assertThrows(
        IllegalStateException.class,
        () ->
            ChPromQlTsClient.ensureSingleHistogramRepresentation(
                List.of(explicit), List.of(exponential)));
  }
}
