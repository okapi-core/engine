/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.stats;

import org.apache.datasketches.kll.KllFloatsSketch;
import org.apache.datasketches.memory.Memory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.okapi.metrics.common.MetricsContext;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RolledUpStatisticsTest {

  public static Stream<Arguments> fuzzyTestDataConfigs() {
    return Stream.of(
        Arguments.of(100.f, 0.1f, 1000),
        Arguments.of(50.f, 0.05f, 500),
        Arguments.of(200.f, 0.2f, 2000),
        Arguments.of(10.f, 0.01f, 100));
  }

  @Test
  public void testSerializeSanity() throws StatisticsFrozenException {
    var stats = new RolledUpStatistics(KllFloatsSketch.newHeapInstance(8));
    var metricsContext = new MetricsContext("trace");
    stats.update(metricsContext, 1.0f);
    stats.update(metricsContext, 2.0f);
    stats.update(metricsContext, 3.0f);
    stats.update(metricsContext, new float[] {4.0f, 5.0f});
    var bytes = stats.serialize();
    assert bytes.length > 0 : "Serialized bytes should not be empty";
    var restoredStats = RolledUpStatistics.deserialize(bytes, new KllSketchRestorer());
    assert restoredStats != null : "Restored statistics should not be null";
    var percentiles = new float[] {0.5f, 0.75f, 0.9f, 0.99f};
    for (float percentile : percentiles) {
      float quantile = restoredStats.percentile(percentile);
      assertEquals(
          quantile,
          stats.percentile(percentile),
          0.01f,
          "Quantile value for " + percentile + " does not match");
    }
  }

  @Test
  public void testDeserialize() {
    var sketch = KllFloatsSketch.newHeapInstance(8);
    sketch.update(1.0f);
    sketch.update(2.0f);

    var bytes = sketch.toByteArray();
    var restoredSketch = KllFloatsSketch.heapify(Memory.wrap(bytes));
    assert restoredSketch.getK() == sketch.getK() : "K value does not match";
    assert restoredSketch.getN() == sketch.getN() : "N value does not match";
    assert restoredSketch.getQuantile(0.5f) == sketch.getQuantile(0.5f)
        : "Quantile value does not match";
    assert restoredSketch.getQuantile(0.99f) == sketch.getQuantile(0.99f)
        : "Expected quantile value is 1.5";
  }
}
