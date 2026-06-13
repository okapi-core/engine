/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics;

import org.junit.jupiter.api.Test;
import org.okapi.io.NotEnoughBytesException;
import org.okapi.io.StreamReadingException;
import org.okapi.metrics.primitives.HistoBlock;
import org.okapi.primitives.Histogram;
import org.okapi.primitives.ReadonlyHistogram;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HistoBlockTests {

  @Test
  void testSimpleBlock() throws IOException, StreamReadingException, NotEnoughBytesException {
    var histo = new HistoBlock();
    var ref =
        new Histogram(
            1000L,
            null,
            Histogram.TEMPORALITY.DELTA,
            new long[] {1, 3, 3, 4},
            new float[] {0.1f, 0.5f, 0.9f});
    histo.updateHistogram(1000L, ref);

    var serialized = histo.toChecksummedByteArray();
    var deserializedHisto = new HistoBlock();
    deserializedHisto.fromChecksummedByteArray(serialized, 0, serialized.length);
    var deserializedHistogram = deserializedHisto.getHistogram(1000L);
    assertHistoEqual(deserializedHistogram.get(), ref);
  }

  @Test
  void testWithTwo() throws IOException, StreamReadingException, NotEnoughBytesException {
    var refA =
        new Histogram(
            2000L,
            null,
            Histogram.TEMPORALITY.DELTA,
            new long[] {2, 4, 4, 1},
            new float[] {0.2f, 0.6f, 0.8f});
    var refB =
        new Histogram(
            3000L,
            null,
            Histogram.TEMPORALITY.CUMULATIVE,
            new long[] {3, 1, 0, 0},
            new float[] {0.3f, 0.5f, 0.7f});
    var histo = new HistoBlock();
    histo.updateHistogram(2000L, refA);
    histo.updateHistogram(3000L, refB);
    var serialized = histo.toChecksummedByteArray();
    var deserializedHisto = new HistoBlock();
    deserializedHisto.fromChecksummedByteArray(serialized, 0, serialized.length);
    var deserializedA = deserializedHisto.getHistogram(2000L);
    assertHistoEqual(deserializedA.get(), refA);
    var deserializedB = deserializedHisto.getHistogram(3000L);
    assertHistoEqual(deserializedB.get(), refB);
  }

  public void assertHistoEqual(Histogram A, Histogram B) {
    assertEquals(A.getStartTs(), B.getStartTs());
    assertEquals(A.getTemporality(), B.getTemporality());
    assertEquals(A.getBucketCounts().length, B.getBucketCounts().length);
    for (int i = 0; i < A.getBucketCounts().length; i++) {
      assertEquals(A.getBucketCounts()[i], B.getBucketCounts()[i]);
    }
    for (int i = 0; i < A.getBuckets().length; i++) {
      assertEquals(A.getBuckets()[i], B.getBuckets()[i]);
    }
  }

  public void assertHistoEqual(ReadonlyHistogram A, Histogram B) {
    assertEquals(A.getStartTs(), B.getStartTs());
    assertEquals(A.getTemporality(), B.getTemporality());
    assertEquals(A.getBucketCounts().size(), B.getBucketCounts().length);
    for (int i = 0; i < A.getBucketCounts().size(); i++) {
      assertEquals(A.getBucketCounts().get(i), B.getBucketCounts()[i]);
    }
    for (int i = 0; i < A.getBuckets().size(); i++) {
      assertEquals(A.getBuckets().get(i), B.getBuckets()[i]);
    }
  }
}
