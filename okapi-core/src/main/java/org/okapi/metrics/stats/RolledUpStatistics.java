/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.stats;

import com.google.common.primitives.Ints;
import lombok.Getter;
import org.apache.datasketches.kll.KllFloatsSketch;
import org.okapi.metrics.common.MetricsContext;
import org.okapi.metrics.pojos.AGG_TYPE;

public class RolledUpStatistics implements UpdatableStatistics {
  @Getter private float sum;
  @Getter private float count;
  @Getter private float sumOfDeviationsSquared; // variance accumulator (optional)
  private KllFloatsSketch floatsQuantiles;

  public RolledUpStatistics(KllFloatsSketch floatsQuantiles) {
    this.sum = 0f;
    this.count = 0f;
    this.sumOfDeviationsSquared = 0f;
    this.floatsQuantiles = floatsQuantiles;
  }

  protected RolledUpStatistics(float sum, float count, KllFloatsSketch quantilesFloatsAPI) {
    this.sum = sum;
    this.count = count;
    this.floatsQuantiles = quantilesFloatsAPI;
  }

  public void fromBytearray(byte[] bytes, QuantileRestorer restorer) {
    if (bytes.length < 8) {
      throw new IllegalArgumentException("Not enough bytes to deserialize Statistics");
    }
    var sumBytes = new byte[4];
    var countBytes = new byte[4];
    System.arraycopy(bytes, 0, sumBytes, 0, 4);
    System.arraycopy(bytes, 4, countBytes, 0, 4);

    float sum = Float.intBitsToFloat(Ints.fromByteArray(sumBytes));
    float count = Float.intBitsToFloat(Ints.fromByteArray(countBytes));
    var quantileBytes = new byte[bytes.length - 8];
    System.arraycopy(bytes, 8, quantileBytes, 0, quantileBytes.length);

    KllFloatsSketch quantiles;
    try {
      quantiles = restorer.restoreQuantiles(quantileBytes);
    } catch (Exception e) {
      throw new RuntimeException("Failed to restore quantiles", e);
    }
    this.sum = sum;
    this.count = count;
    this.floatsQuantiles = quantiles;
  }

  private RolledUpStatistics() {}

  public static RolledUpStatistics deserialize(byte[] bytes, QuantileRestorer quantileRestorer) {
    var stats = new RolledUpStatistics();
    if (bytes.length < 8) {
      throw new IllegalArgumentException("Not enough bytes to deserialize Statistics");
    }
    stats.fromBytearray(bytes, quantileRestorer);
    return stats;
  }

  @Override
  public void update(MetricsContext ctx, float f) throws StatisticsFrozenException {
    sum += f;
    var mean = sum / count;
    var deviation = f - mean;
    sumOfDeviationsSquared += deviation * deviation;
    count++;
    floatsQuantiles.update(f);
  }

  @Override
  public void update(MetricsContext ctx, float[] arr) throws StatisticsFrozenException {
    for (int i = 0; i < arr.length; i++) {
      // route through same logic, but we’re already under the write lock
      final float x = arr[i];
      final float prevCount = count;
      final float prevSum = sum;

      if (prevCount == 0f) {
        sum = x;
        count = 1f;
        sumOfDeviationsSquared = 0f;
      } else {
        final float prevMean = prevSum / prevCount;
        sum = prevSum + x;
        final float newCount = prevCount + 1f;
        final float newMean = sum / newCount;
        sumOfDeviationsSquared += (x - prevMean) * (x - newMean);
        count = newCount;
      }
      floatsQuantiles.update(x);
    }
  }

  @Override
  public float percentile(double quantile) {
    return floatsQuantiles.getQuantile(quantile);
  }

  @Override
  public float avg() {
    return count == 0f ? 0f : (sum / count);
  }

  @Override
  public float min() {
    return floatsQuantiles.getQuantile(0.0);
  }

  @Override
  public float max() {
    return floatsQuantiles.getQuantile(1.0);
  }

  @Override
  public byte[] serialize() {
    // todo: without this, KLLSketch flags get messed which makes serialization non-deterministic.
    min();
    var sumAsInt = Ints.toByteArray(Float.floatToIntBits(sum));
    var countAsInt = Ints.toByteArray(Float.floatToIntBits(count));
    var quantileBytes = floatsQuantiles.toByteArray();

    var bytes = new byte[4 + 4 + quantileBytes.length];
    System.arraycopy(sumAsInt, 0, bytes, 0, 4);
    System.arraycopy(countAsInt, 0, bytes, 4, 4);
    System.arraycopy(quantileBytes, 0, bytes, 8, quantileBytes.length);
    return bytes;
  }

  @Override
  public float aggregate(AGG_TYPE aggType) {
    return switch (aggType) {
      case SUM -> sum;
      case COUNT -> count;
      case AVG -> count == 0f ? 0f : (sum / count);
      case MIN -> floatsQuantiles.getQuantile(0.0);
      case MAX -> floatsQuantiles.getQuantile(1.0);
      case P50 -> floatsQuantiles.getQuantile(0.5);
      case P75 -> floatsQuantiles.getQuantile(0.75);
      case P90 -> floatsQuantiles.getQuantile(0.9);
      case P95 -> floatsQuantiles.getQuantile(0.95);
      case P99 -> floatsQuantiles.getQuantile(0.99);
    };
  }

  @Override
  public boolean freeze() {
    return true;
  }

  protected KllFloatsSketch getSketch() {
    return this.floatsQuantiles;
  }
}
