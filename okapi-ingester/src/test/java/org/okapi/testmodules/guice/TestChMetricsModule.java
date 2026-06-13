/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.testmodules.guice;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.okapi.metrics.ch.ChMetricsIngester;
import org.okapi.metrics.ch.ChMetricsQueryProcessor;
import org.okapi.metrics.ch.ChMetricsWalConsumer;
import org.okapi.metrics.ch.ChMetricsWalConsumerDriver;
import org.okapi.metrics.ch.ChSearchMetricsProcessor;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.metrics.ch.ChWriter;
import org.okapi.metrics.ch.SumQueryProcessor;
import org.okapi.metrics.ch.template.ChMetricTemplateEngine;
import org.okapi.metrics.core.MetricsEventEmitter;
import org.okapi.metrics.core.WalMetricsEventEmitter;
import org.okapi.metrics.otel.OtelConverter;
import org.okapi.promql.ch.ChPromQlSeriesDiscoveryFactory;
import org.okapi.promql.ch.ChPromQlTsClientFactory;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.query.NoOpStatisticsMerger;
import org.okapi.promql.query.PromQlQueryProcessor;
import org.okapi.promql.runtime.SeriesDiscoveryFactory;
import org.okapi.promql.runtime.TsClientFactory;
import org.okapi.wal.manager.WalManager;

public class TestChMetricsModule extends AbstractModule {
  private final Path walDir;
  private final int batchSize;

  public TestChMetricsModule(Path walDir, int batchSize) {
    this.walDir = walDir;
    this.batchSize = batchSize;
  }

  @Provides
  @Singleton
  Client provideClient() {
    return getChClient();
  }

  @Provides
  @Singleton
  WalManager.WalConfig provideWalConfig() {
    return new WalManager.WalConfig(1_048_576);
  }

  @Provides
  @Singleton
  ChWalResources provideWalResources(WalManager.WalConfig walConfig) throws IOException {
    Files.createDirectories(walDir);
    return new ChWalResources(walDir, walConfig);
  }

  @Provides
  @Singleton
  ChWriter provideChWriter(Client client) {
    return new ChWriter(client);
  }

  @Provides
  @Singleton
  ChMetricsIngester provideChMetricsIngester(OtelConverter converter, ChWalResources walResources) {
    return new ChMetricsIngester(converter, walResources);
  }

  @Provides
  @Singleton
  MetricsEventEmitter provideMetricsEventEmitter(ChWalResources walResources) {
    return new WalMetricsEventEmitter(walResources);
  }

  @Provides
  @Singleton
  ChMetricsWalConsumer provideChMetricsWalConsumer(
      MetricsEventEmitter eventEmitter, ChWriter writer) {
    return new ChMetricsWalConsumer(batchSize, writer, eventEmitter);
  }

  @Provides
  @Singleton
  ChMetricsWalConsumerDriver provideChMetricsWalConsumerDriver(ChMetricsWalConsumer consumer) {
    return new ChMetricsWalConsumerDriver(consumer);
  }

  @Provides
  @Singleton
  ChMetricsQueryProcessor provideChQueryProcessor(
      Client client, ChMetricTemplateEngine templateEngine) {
    return new ChMetricsQueryProcessor(client, templateEngine);
  }

  @Provides
  @Singleton
  SumQueryProcessor provideSumQueryProcessor(Client client, ChMetricTemplateEngine templateEngine) {
    return new SumQueryProcessor(client, templateEngine);
  }

  @Provides
  @Singleton
  ChSearchMetricsProcessor provideChSearchMetricsProcessor(
      Client client, ChMetricTemplateEngine templateEngine) {
    return new ChSearchMetricsProcessor(client, templateEngine);
  }

  @Provides
  @Singleton
  OtelConverter provideOtelConverter() {
    return new OtelConverter();
  }

  @Provides
  @Singleton
  ExecutorService providePromQlExecutor() {
    return Executors.newFixedThreadPool(2);
  }

  @Provides
  @Singleton
  StatisticsMerger provideStatisticsMerger() {
    return new NoOpStatisticsMerger();
  }

  @Provides
  @Singleton
  TsClientFactory provideTsClientFactory(Client client, ChMetricTemplateEngine templateEngine) {
    return new ChPromQlTsClientFactory(client, templateEngine);
  }

  @Provides
  @Singleton
  SeriesDiscoveryFactory provideSeriesDiscoveryFactory(
      Client client, ChMetricTemplateEngine templateEngine) {
    return new ChPromQlSeriesDiscoveryFactory(client, templateEngine);
  }

  @Provides
  @Singleton
  PromQlQueryProcessor providePromQlQueryProcessor(
      ExecutorService exec,
      StatisticsMerger merger,
      TsClientFactory tsClientFactory,
      SeriesDiscoveryFactory seriesDiscoveryFactory) {
    return new PromQlQueryProcessor(exec, merger, tsClientFactory, seriesDiscoveryFactory);
  }

  private Client getChClient() {
    return new Client.Builder()
        .addEndpoint(Protocol.HTTP, "localhost", 8123, false)
        .setUsername("default")
        .setPassword("okapi_testing_password")
        .build();
  }

  @Provides
  @Singleton
  ChMetricTemplateEngine provideTemplateEngine() {
    return new ChMetricTemplateEngine();
  }
}
