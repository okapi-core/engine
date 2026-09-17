/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import java.util.List;
import org.okapi.logs.ch.ChLogsWalConsumer;
import org.okapi.logs.ch.ChLogsWalConsumerDriver;
import org.okapi.logs.ch.OtelLogsToChRowsConverter;
import org.okapi.logs.core.LogsEventEmitter;
import org.okapi.metrics.ch.ChMetricsQueryProcessor;
import org.okapi.metrics.ch.ChMetricsWalConsumer;
import org.okapi.metrics.ch.ChMetricsWalConsumerDriver;
import org.okapi.metrics.ch.ChWriter;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.metrics.core.MetricsEventEmitter;
import org.okapi.runtime.ch.ChWalConsumerCommonDriver;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.properties.ChWalConsumerCfg;
import org.okapi.telemetry.OkapiInternalMetrics;
import org.okapi.traces.ch.*;
import org.okapi.traces.core.TracesEventEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_CH)
public class ChWalConsumersConfig {

  @Bean
  public ChWriter chWriter(
      @Autowired com.clickhouse.client.api.Client client, @Autowired OkapiInternalMetrics metrics) {
    return new ChWriter(client, metrics);
  }

  @Bean
  public ChMetricsWalConsumer chMetricsWalConsumer(
      @Autowired MetricsEventEmitter eventEmitter,
      @Autowired ChWriter writer,
      @Autowired ChWalConsumerCfg walCfg,
      @Autowired OkapiInternalMetrics metrics) {
    return new ChMetricsWalConsumer(walCfg.getBatchSize(), writer, eventEmitter, metrics);
  }

  @Bean
  public ChMetricsWalConsumerDriver chMetricsWalConsumerDriver(
      @Autowired ChMetricsWalConsumer walConsumer) {
    return new ChMetricsWalConsumerDriver(walConsumer);
  }

  @Bean
  public ChWalConsumerCommonDriver chWalConsumerCommonDriver(
      @Autowired ChMetricsWalConsumerDriver metricsDriver,
      @Autowired ChTracesWalConsumerDriver tracesDriver,
      @Autowired ChLogsWalConsumerDriver logsDriver) {
    return new ChWalConsumerCommonDriver(List.of(metricsDriver, tracesDriver, logsDriver));
  }

  @Bean
  public OtelLogsToChRowsConverter otelLogsToChRowsConverter() {
    return new OtelLogsToChRowsConverter();
  }

  @Bean
  public ChLogsWalConsumer chLogsWalConsumer(
      @Autowired LogsEventEmitter eventEmitter,
      @Autowired ChWriter writer,
      @Autowired ChWalConsumerCfg walCfg,
      @Autowired OtelLogsToChRowsConverter converter,
      @Autowired OkapiInternalMetrics metrics) {
    return new ChLogsWalConsumer(walCfg.getBatchSize(), writer, eventEmitter, converter, metrics);
  }

  @Bean
  public ChLogsWalConsumerDriver chLogsWalConsumerDriver(@Autowired ChLogsWalConsumer walConsumer) {
    return new ChLogsWalConsumerDriver(walConsumer);
  }

  @Bean
  public OtelTracesToChRowsConverter otelTracesToChRowsConverter() {
    return new OtelTracesToChRowsConverter();
  }

  @Bean
  public ChTracesWalConsumer chTracesWalConsumer(
      @Autowired TracesEventEmitter eventEmitter,
      @Autowired ChWriter writer,
      @Autowired ChWalConsumerCfg walCfg,
      @Autowired OtelTracesToChRowsConverter converter,
      @Autowired TraceFilterStrategy traceFilterStrategy,
      @Autowired SpanFilterStrategy spanFilterStrategy,
      @Autowired OkapiInternalMetrics metrics) {
    return new ChTracesWalConsumer(
        eventEmitter,
        walCfg.getBatchSize(),
        writer,
        converter,
        traceFilterStrategy,
        spanFilterStrategy,
        metrics);
  }

  @Bean
  public ChTracesWalConsumerDriver chTracesWalConsumerDriver(
      @Autowired ChTracesWalConsumer walConsumer) {
    return new ChTracesWalConsumerDriver(walConsumer);
  }

  @Bean
  public ChMetricsQueryProcessor chMetricsQueryProcessor(
      @Autowired com.clickhouse.client.api.Client client,
      @Autowired ChMetricTemplateEngine templateEngine) {
    return new ChMetricsQueryProcessor(client, templateEngine);
  }
}
