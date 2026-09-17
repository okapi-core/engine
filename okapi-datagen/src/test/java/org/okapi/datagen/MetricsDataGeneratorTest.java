/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.proto.metrics.v1.Metric;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.datagen.spans.MetricsDataGenConfig;
import org.okapi.datagen.spans.MetricsDataGenerator;

class MetricsDataGeneratorTest {
  @Test
  void metricSpecDefaultsToTenPercentExemplars() {
    assertEquals(
        MetricsDataGenConfig.DEFAULT_EXEMPLAR_PROBABILITY,
        MetricsDataGenConfig.MetricSpec.builder().build().getExemplarProbability());
  }

  @Test
  void emitsConfiguredExemplarsForAllMetricPointTypes() {
    var distribution =
        MetricsDataGenConfig.DistributionSpec.builder()
            .type(MetricsDataGenConfig.DistributionType.LOG_NORMAL)
            .mu(0.0)
            .sigma(1.0)
            .build();
    var metrics =
        List.of(
            metric("gauge", MetricsDataGenConfig.MetricType.GAUGE, distribution),
            metric("sum", MetricsDataGenConfig.MetricType.SUM, distribution),
            metric("histo", MetricsDataGenConfig.MetricType.HISTO, distribution));
    var config =
        MetricsDataGenConfig.builder()
            .seed(42)
            .timeWindowMs(1_000)
            .gaugeSamplingRate(1)
            .sumHistogramIntervalMs(1_000)
            .hostMetrics(metrics)
            .kafkaMetrics(List.of())
            .spanMetrics(List.of())
            .build();

    var generator = new MetricsDataGenerator(config);
    var generated = generator.generate().get(0);
    assertEquals(4, generator.countExemplars(List.of(generated)));

    generated
        .getResourceMetrics(0)
        .getScopeMetrics(0)
        .getMetricsList()
        .forEach(this::assertHasExemplar);
  }

  private MetricsDataGenConfig.MetricSpec metric(
      String name,
      MetricsDataGenConfig.MetricType type,
      MetricsDataGenConfig.DistributionSpec distribution) {
    return MetricsDataGenConfig.MetricSpec.builder()
        .name(name)
        .type(type)
        .distribution(distribution)
        .exemplarProbability(1.0)
        .histogramCountMean(10.0)
        .build();
  }

  private void assertHasExemplar(Metric metric) {
    if (metric.hasGauge()) {
      assertEquals(1, metric.getGauge().getDataPoints(0).getExemplarsCount());
    } else if (metric.hasSum()) {
      assertEquals(1, metric.getSum().getDataPoints(0).getExemplarsCount());
    } else {
      assertTrue(metric.hasHistogram());
      assertEquals(1, metric.getHistogram().getDataPoints(0).getExemplarsCount());
    }
  }
}
