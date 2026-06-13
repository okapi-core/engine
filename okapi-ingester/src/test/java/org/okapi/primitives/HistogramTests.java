/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import org.junit.jupiter.api.Test;
import org.okapi.io.StreamReadingException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HistogramTests {

  @Test
  void testHistogram_singleSample() throws IOException, StreamReadingException {
    var histogram =
        new Histogram(
            1L, 2L, Histogram.TEMPORALITY.DELTA, new int[] {1, 0, 0}, new float[] {10.0f, 20.0f});
    var serialied = histogram.toByteArray();
    var deserialized = new Histogram();
    deserialized.fromByteArray(serialied, 0, serialied.length);
    assert histogram.startTs == deserialized.startTs;
    assert histogram.endTs.equals(deserialized.endTs);
    assert histogram.temporality == deserialized.temporality;
    assert histogram.bucketCounts.length == deserialized.bucketCounts.length;
    assertArrayEquals(histogram.getBuckets(), deserialized.getBuckets());
    assertArrayEquals(histogram.getBucketCounts(), deserialized.getBucketCounts());
  }

  @Test
  void testHistogram_multipleSamples() throws IOException, StreamReadingException {
    var histogram =
        new Histogram(
            1L,
            null,
            Histogram.TEMPORALITY.CUMULATIVE,
            new long[] {5, 10, 15, 20},
            new float[] {10.0f, 20.0f, 30.0f});
    var serialied = histogram.toByteArray();
    var deserialized = new Histogram();
    deserialized.fromByteArray(serialied, 0, serialied.length);
    assert histogram.startTs == deserialized.startTs;
    assertEquals(histogram.endTs, deserialized.endTs);
    assert histogram.temporality == deserialized.temporality;
    assert histogram.bucketCounts.length == deserialized.bucketCounts.length;
    assertArrayEquals(histogram.getBuckets(), deserialized.getBuckets());
    assertArrayEquals(histogram.getBucketCounts(), deserialized.getBucketCounts());
  }

  @Test
  void testHistogram_empty() throws IOException, StreamReadingException {
    var histogram =
        new Histogram(0L, 0L, Histogram.TEMPORALITY.DELTA, new long[] {0, 0}, new float[] {10.0f});
    var serialied = histogram.toByteArray();
    var deserialized = new Histogram();
    deserialized.fromByteArray(serialied, 0, serialied.length);
    assert histogram.startTs == deserialized.startTs;
    assert histogram.endTs.equals(deserialized.endTs);
    assert histogram.temporality == deserialized.temporality;
    assert histogram.bucketCounts.length == deserialized.bucketCounts.length;
    assertArrayEquals(histogram.getBuckets(), deserialized.getBuckets());
    assertArrayEquals(histogram.getBucketCounts(), deserialized.getBucketCounts());
  }

  @Test
  void testHistogram_WithoutEndtime() throws IOException, StreamReadingException {
    var histogram =
        new Histogram(
            1L,
            null,
            Histogram.TEMPORALITY.CUMULATIVE,
            new int[] {3, 6, 9},
            new float[] {5.0f, 15.0f});
    var serialied = histogram.toByteArray();
    var deserialized = new Histogram();
    deserialized.fromByteArray(serialied, 0, serialied.length);
    assert histogram.startTs == deserialized.startTs;
    assertEquals(histogram.endTs, deserialized.endTs);
    assert histogram.temporality == deserialized.temporality;
    assert histogram.bucketCounts.length == deserialized.bucketCounts.length;
    assertArrayEquals(histogram.getBuckets(), deserialized.getBuckets());
    assertArrayEquals(histogram.getBucketCounts(), deserialized.getBucketCounts());
  }
}
