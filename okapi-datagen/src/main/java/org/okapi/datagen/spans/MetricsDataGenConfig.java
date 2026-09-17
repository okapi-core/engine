/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MetricsDataGenConfig {
  public static final double DEFAULT_EXEMPLAR_PROBABILITY = 0.1;

  long seed;
  long timeWindowMs;
  double gaugeSamplingRate;
  long sumHistogramIntervalMs;
  String kafkaCluster;
  @Builder.Default List<String> kafkaBrokers = List.of("broker-1", "broker-2");

  @Builder.Default
  List<Double> defaultHistogramBounds =
      List.of(1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0);

  @Builder.Default List<MetricSpec> hostMetrics = defaultHostMetrics();
  @Builder.Default List<MetricSpec> kafkaMetrics = defaultKafkaMetrics();
  @Builder.Default List<MetricSpec> spanMetrics = defaultSpanMetrics();

  public static MetricsDataGenConfig defaultConfig() {
    return MetricsDataGenConfig.builder()
        .seed(42L)
        .timeWindowMs(300_000L)
        .gaugeSamplingRate(1.0)
        .sumHistogramIntervalMs(10_000L)
        .kafkaCluster("astro-kafka")
        .build();
  }

  @Value
  @Builder
  public static class MetricSpec {
    String name;
    MetricType type;
    String unit;
    DistributionSpec distribution;
    @Builder.Default Map<String, String> tags = Map.of();
    @Builder.Default List<Double> histogramBounds = List.of();
    @Builder.Default Double histogramCountMean = null;

    /** Probability that each generated data point carries an exemplar. */
    @Builder.Default Double exemplarProbability = DEFAULT_EXEMPLAR_PROBABILITY;
  }

  @Value
  @Builder
  public static class DistributionSpec {
    DistributionType type;
    Double alpha;
    Double beta;
    Double mu;
    Double sigma;
    Double mean;
    Double p;
    Double min;
    Double max;
    Double scale;
  }

  public enum MetricType {
    GAUGE,
    SUM,
    HISTO
  }

  public enum DistributionType {
    BETA,
    LOG_NORMAL,
    POISSON,
    BERNOULLI,
    LOGNORMAL_X_POISSON
  }

  private static List<MetricSpec> defaultHostMetrics() {
    var hosts = List.of("node-1", "node-2", "node-3");
    var out = new java.util.ArrayList<MetricSpec>();
    for (var host : hosts) {
      out.add(
          MetricSpec.builder()
              .name("system.cpu.utilization")
              .type(MetricType.GAUGE)
              .unit("1")
              .tags(Map.of("host.name", host))
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.BETA)
                      .alpha(2.5)
                      .beta(5.0)
                      .min(0.0)
                      .max(1.0)
                      .build())
              .build());
      out.add(
          MetricSpec.builder()
              .name("system.memory.utilization")
              .type(MetricType.GAUGE)
              .unit("1")
              .tags(Map.of("host.name", host))
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.BETA)
                      .alpha(3.0)
                      .beta(3.5)
                      .min(0.0)
                      .max(1.0)
                      .build())
              .build());
      out.add(
          MetricSpec.builder()
              .name("system.memory.usage")
              .type(MetricType.GAUGE)
              .unit("By")
              .tags(Map.of("host.name", host))
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.LOG_NORMAL)
                      .mu(20.2)
                      .sigma(0.45)
                      .build())
              .build());
      out.add(
          MetricSpec.builder()
              .name("system.disk.utilization")
              .type(MetricType.GAUGE)
              .unit("1")
              .tags(Map.of("host.name", host))
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.BETA)
                      .alpha(2.2)
                      .beta(3.8)
                      .min(0.0)
                      .max(1.0)
                      .build())
              .build());
      out.add(
          MetricSpec.builder()
              .name("system.network.io")
              .type(MetricType.GAUGE)
              .unit("By/s")
              .tags(Map.of("host.name", host))
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.LOG_NORMAL)
                      .mu(13.0)
                      .sigma(0.8)
                      .build())
              .build());
    }
    return out;
  }

  private static List<MetricSpec> defaultKafkaMetrics() {
    var list =
        List.of(
            MetricSpec.builder()
                .name("kafka.consumer.lag")
                .type(MetricType.GAUGE)
                .unit("records")
                .distribution(
                    DistributionSpec.builder()
                        .type(DistributionType.LOG_NORMAL)
                        .mu(4.0)
                        .sigma(1.1)
                        .min(0.0)
                        .max(1_000_000.0)
                        .build())
                .build(),
            MetricSpec.builder()
                .name("kafka.controller.active_count")
                .type(MetricType.GAUGE)
                .unit("1")
                .distribution(
                    DistributionSpec.builder().type(DistributionType.BERNOULLI).p(0.98).build())
                .build(),
            MetricSpec.builder()
                .name("kafka.partition.offline")
                .type(MetricType.GAUGE)
                .unit("partitions")
                .distribution(
                    DistributionSpec.builder().type(DistributionType.POISSON).mean(0.05).build())
                .build(),
            MetricSpec.builder()
                .name("kafka.request.count")
                .type(MetricType.SUM)
                .unit("requests")
                .distribution(
                    DistributionSpec.builder().type(DistributionType.POISSON).mean(120.0).build())
                .build(),
            MetricSpec.builder()
                .name("kafka.message.bytes")
                .type(MetricType.SUM)
                .unit("By")
                .distribution(
                    DistributionSpec.builder()
                        .type(DistributionType.LOGNORMAL_X_POISSON)
                        .mu(16.0)
                        .sigma(0.9)
                        .mean(80.0)
                        .build())
                .build(),
            MetricSpec.builder()
                .name("kafka.network.io")
                .type(MetricType.SUM)
                .unit("By")
                .distribution(
                    DistributionSpec.builder()
                        .type(DistributionType.LOG_NORMAL)
                        .mu(16.5)
                        .sigma(0.9)
                        .build())
                .build());
    var partitions = List.of(0, 1, 2);
    var paritionMetrics = new ArrayList<MetricSpec>();
    for (var part : partitions) {
      paritionMetrics.add(
          MetricSpec.builder()
              .name("kafka.request.latency")
              .type(MetricType.HISTO)
              .tags(Map.of("partition", "part-" + part))
              .unit("ms")
              .histogramCountMean(60.0)
              .distribution(
                  DistributionSpec.builder()
                      .type(DistributionType.LOG_NORMAL)
                      .mu(3.6)
                      .sigma(0.55)
                      .build())
              .build());
    }
    return Collections.unmodifiableList(
        new ArrayList<>() {
          {
            addAll(paritionMetrics);
            addAll(list);
          }
        });
  }

  private static List<MetricSpec> defaultSpanMetrics() {
    var out = new java.util.ArrayList<MetricSpec>();
    addSpanMetricSet(out, "frontend/purchase", 40.0, 0.02, 5.3, 0.5);
    addSpanMetricSet(out, "frontend/home", 80.0, 0.01, 4.2, 0.45);
    addSpanMetricSet(out, "frontend/search", 90.0, 0.015, 4.6, 0.5);
    addSpanMetricSet(out, "catalog/search", 120.0, 0.01, 4.5, 0.45);
    addSpanMetricSet(out, "catalog/product-details", 90.0, 0.01, 4.6, 0.45);
    addSpanMetricSet(out, "catalog/recommendations", 60.0, 0.02, 4.8, 0.5);
    addSpanMetricSet(out, "cart/add", 70.0, 0.015, 4.7, 0.5);
    addSpanMetricSet(out, "cart/remove", 40.0, 0.01, 4.4, 0.45);
    addSpanMetricSet(out, "cart/checkout", 35.0, 0.02, 4.9, 0.55);
    addSpanMetricSet(out, "checkout/complete", 35.0, 0.03, 5.1, 0.55);
    addSpanMetricSet(out, "checkout/price", 55.0, 0.015, 4.7, 0.5);
    addSpanMetricSet(out, "checkout/shipping", 40.0, 0.02, 4.8, 0.5);
    addSpanMetricSet(out, "payment/authorize", 30.0, 0.05, 5.0, 0.6);
    addSpanMetricSet(out, "payment/capture", 25.0, 0.04, 5.1, 0.6);
    addSpanMetricSet(out, "payment/refund", 10.0, 0.03, 5.2, 0.65);
    addSpanMetricSet(out, "payment-gateway/charge", 30.0, 0.06, 5.2, 0.65);
    addSpanMetricSet(out, "payment-gateway/refund", 12.0, 0.05, 5.3, 0.7);
    addSpanMetricSet(out, "inventory/reserve", 40.0, 0.03, 4.9, 0.55);
    addSpanMetricSet(out, "inventory/release", 30.0, 0.02, 4.6, 0.5);
    addSpanMetricSet(out, "inventory/check", 50.0, 0.015, 4.5, 0.45);
    addSpanMetricSet(out, "shipping/quote", 30.0, 0.02, 4.8, 0.5);
    addSpanMetricSet(out, "shipping/create-label", 20.0, 0.02, 5.0, 0.55);
    addSpanMetricSet(out, "shipping/track", 25.0, 0.015, 4.6, 0.45);
    addSpanMetricSet(out, "orders-db/write", 35.0, 0.02, 4.7, 0.6);
    addSpanMetricSet(out, "orders-db/read", 80.0, 0.01, 4.2, 0.45);
    addSpanMetricSet(out, "notification/send-confirmation", 25.0, 0.01, 4.3, 0.5);
    addSpanMetricSet(out, "notification/send-shipping", 20.0, 0.01, 4.2, 0.45);
    return out;
  }

  private static void addSpanMetricSet(
      List<MetricSpec> out,
      String spanName,
      double callsMean,
      double errorRate,
      double durationMu,
      double durationSigma) {
    var tags = Map.of("span.name", spanName);
    out.add(
        MetricSpec.builder()
            .name("spanmetrics.calls")
            .type(MetricType.SUM)
            .unit("1")
            .tags(tags)
            .distribution(
                DistributionSpec.builder().type(DistributionType.POISSON).mean(callsMean).build())
            .build());
    out.add(
        MetricSpec.builder()
            .name("spanmetrics.errors")
            .type(MetricType.SUM)
            .unit("1")
            .tags(tags)
            .distribution(
                DistributionSpec.builder()
                    .type(DistributionType.POISSON)
                    .mean(callsMean * errorRate)
                    .build())
            .build());
    out.add(
        MetricSpec.builder()
            .name("spanmetrics.duration")
            .type(MetricType.HISTO)
            .unit("ms")
            .tags(tags)
            .exemplarProbability(0.10)
            .histogramCountMean(callsMean)
            .distribution(
                DistributionSpec.builder()
                    .type(DistributionType.LOG_NORMAL)
                    .mu(durationMu)
                    .sigma(durationSigma)
                    .build())
            .build());
  }
}
