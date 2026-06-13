/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.primitives;

import lombok.Getter;
import org.okapi.io.OkapiIo;
import org.okapi.io.StreamReadingException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Getter
public class Histogram implements RawSerializable {
  public enum TEMPORALITY {
    DELTA,
    CUMULATIVE
  }

  long startTs;
  Long endTs;
  TEMPORALITY temporality;
  long[] bucketCounts;
  float[] buckets;

  public Histogram(
      long startTs, Long endTs, TEMPORALITY temporality, long[] bucketCounts, float[] buckets) {
    this.startTs = startTs;
    this.endTs = endTs;
    this.temporality = temporality;
    this.bucketCounts = bucketCounts;
    this.buckets = buckets;
  }

  public Histogram(
      long startTs, Long endTs, TEMPORALITY temporality, int[] bucketCounts, float[] buckets) {
    this.startTs = startTs;
    this.endTs = endTs;
    this.temporality = temporality;
    this.bucketCounts = new long[bucketCounts.length];
    for (int i = 0; i < bucketCounts.length; i++) {
      this.bucketCounts[i] = bucketCounts[i];
    }
    this.buckets = buckets;
  }

  public Histogram() {}

  @Override
  public void fromByteArray(byte[] bytes, int off, int len)
      throws StreamReadingException, IOException {
    var is = new ByteArrayInputStream(bytes, off, len);
    var temp = OkapiIo.readInt(is);
    switch (temp) {
      case 0:
        temporality = TEMPORALITY.DELTA;
        break;
      case 1:
        temporality = TEMPORALITY.CUMULATIVE;
        break;
      default:
        throw new IOException("Unknown temporality value: " + temp);
    }
    startTs = OkapiIo.readLong(is);
    endTs = OkapiIo.readLong(is);
    if (endTs == -1L) {
      endTs = null;
    }
    var length = OkapiIo.readInt(is);
    bucketCounts = new long[length];
    for (int i = 0; i < length; i++) {
      bucketCounts[i] = OkapiIo.readLong(is);
    }
    buckets = new float[length - 1];
    for (int i = 0; i < length - 1; i++) {
      buckets[i] = OkapiIo.readFloat(is);
    }
  }

  @Override
  public int byteSize() {
    return 4 // temporality
        + 8 // startTs
        + 8 // endTs
        + 4 // length of bucketCounts
        + 8 * bucketCounts.length // bucketCounts
        + 4 * buckets.length; // buckets
  }

  @Override
  public byte[] toByteArray() throws IOException {
    var os = new java.io.ByteArrayOutputStream();
    switch (temporality) {
      case DELTA:
        OkapiIo.writeInt(os, 0); // Placeholder for delta serialization
        break;
      case CUMULATIVE:
        OkapiIo.writeInt(os, 1); // Placeholder for delta serialization
        break;
    }
    OkapiIo.writeLong(os, startTs);
    OkapiIo.writeLong(os, endTs != null ? endTs : -1L);
    OkapiIo.writeInt(os, bucketCounts.length);
    for (var count : bucketCounts) {
      OkapiIo.writeLong(os, count);
    }
    // it is assumed that buckets.length = bucketCounts.length - 1
    for (var bucket : buckets) {
      OkapiIo.writeFloat(os, bucket);
    }
    return os.toByteArray();
  }
}
