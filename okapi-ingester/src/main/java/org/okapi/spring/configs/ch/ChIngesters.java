/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import org.okapi.logs.ch.ChLogsIngester;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.metrics.otel.OtelConverter;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.MetricsConsumptionCfg;
import org.okapi.traces.ch.ChTracesIngester;
import org.okapi.traces.ch.NoopSpanFilterStrategy;
import org.okapi.traces.ch.NoopTraceFilterStrategy;
import org.okapi.traces.ch.SpanFilterStrategy;
import org.okapi.traces.ch.TraceFilterStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChIngesters {

  @Bean
  public ChLogsIngester logsIngester() {
    return new ChLogsIngester();
  }

  @Bean
  public TraceFilterStrategy traceFilterStrategy() {
    return new NoopTraceFilterStrategy();
  }

  @Bean
  public SpanFilterStrategy spanFilterStrategy() {
    return new NoopSpanFilterStrategy();
  }

  @Bean
  public ChTracesIngester tracesIngester(
      @Autowired @Qualifier(Qualifiers.TRACES_CH_WAL_RESOURCES) ChWalResources walResources) {
    return new ChTracesIngester(walResources);
  }

  @Bean
  public ChMetricsIngester metricsIngester(
      @Autowired OtelConverter converter,
      @Autowired @Qualifier(Qualifiers.METRICS_CH_WAL_RESOURCES)
          ObjectProvider<ChWalResources> chWalResources,
      @Autowired MetricsConsumptionCfg cfg) {
    var directIngestionEnabled =
        cfg.getConsumptionType() == MetricsConsumptionCfg.ConsumptionType.WAL;
    return new ChMetricsIngester(
        converter, chWalResources.getIfAvailable(), directIngestionEnabled);
  }
}
