/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.testmodules.guice;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import org.okapi.metrics.ch.ChWriter;
import org.okapi.traces.ch.ChSpanAttributeHintsService;
import org.okapi.traces.ch.ChSpanStatsQueryService;
import org.okapi.traces.ch.ChTraceQueryService;
import org.okapi.traces.ch.ChTracesWalConsumer;
import org.okapi.traces.ch.ChTracesWalConsumerDriver;
import org.okapi.traces.ch.NoopSpanFilterStrategy;
import org.okapi.traces.ch.NoopTraceFilterStrategy;
import org.okapi.traces.ch.OtelTracesToChRowsConverter;
import org.okapi.traces.ch.SpanFilterStrategy;
import org.okapi.traces.ch.TraceFilterStrategy;
import org.okapi.traces.ch.reds.ChRedQueryService;
import org.okapi.traces.ch.template.ChTraceTemplateEngine;
import org.okapi.traces.core.FakeTracesEventEmitter;
import org.okapi.traces.core.TracesEventEmitter;

public class TestChTracesModule extends AbstractModule {
  private final int batchSize;

  public TestChTracesModule(Path ignoredWalDir, int batchSize) {
    this.batchSize = batchSize;
  }

  @Provides
  @Singleton
  Client provideClient() {
    return getChClient();
  }

  @Provides
  @Singleton
  ChWriter provideChWriter(Client client) {
    return new ChWriter(client);
  }

  @Provides
  @Singleton
  TraceFilterStrategy provideTraceFilterStrategy() {
    return new NoopTraceFilterStrategy();
  }

  @Provides
  @Singleton
  SpanFilterStrategy provideSpanFilterStrategy() {
    return new NoopSpanFilterStrategy();
  }

  @Provides
  @Singleton
  FakeTracesEventEmitter provideFakeTracesEventEmitter() {
    return new FakeTracesEventEmitter(List.of());
  }

  @Provides
  @Singleton
  TracesEventEmitter provideTracesEventEmitter(FakeTracesEventEmitter emitter) {
    return emitter;
  }

  @Provides
  @Singleton
  OtelTracesToChRowsConverter provideOtelTracesToChRowsConverter() {
    return new OtelTracesToChRowsConverter();
  }

  @Provides
  @Singleton
  ChTracesWalConsumer provideChTracesWalConsumer(
      TracesEventEmitter eventEmitter,
      ChWriter writer,
      TraceFilterStrategy traceFilterStrategy,
      SpanFilterStrategy spanFilterStrategy,
      OtelTracesToChRowsConverter converter) {
    return new ChTracesWalConsumer(
        eventEmitter, batchSize, writer, converter, traceFilterStrategy, spanFilterStrategy);
  }

  @Provides
  @Singleton
  ChTracesWalConsumerDriver provideChTracesWalConsumerDriver(ChTracesWalConsumer consumer) {
    return new ChTracesWalConsumerDriver(consumer);
  }

  @Provides
  @Singleton
  ChTraceTemplateEngine provideTraceTemplateEngine() {
    return new ChTraceTemplateEngine();
  }

  @Provides
  @Singleton
  ChTraceQueryService provideChTraceQueryService(
      Client client, ChTraceTemplateEngine templateEngine) {
    return new ChTraceQueryService(client, templateEngine);
  }

  @Provides
  @Singleton
  ChRedQueryService provideChRedQueryService(Client client, ChTraceTemplateEngine templateEngine) {
    return new ChRedQueryService(client, templateEngine);
  }

  @Provides
  @Singleton
  ChSpanStatsQueryService provideChSpanStatsQueryService(
      Client client, ChTraceTemplateEngine templateEngine) {
    return new ChSpanStatsQueryService(client, templateEngine);
  }

  @Provides
  @Singleton
  ChSpanAttributeHintsService provideChSpanAttributeHintsService(
      Client client, ChTraceTemplateEngine templateEngine) {
    return new ChSpanAttributeHintsService(client, templateEngine);
  }

  private Client getChClient() {
    return new Client.Builder()
        .addEndpoint(
            Protocol.HTTP,
            System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_HOST", "127.0.0.1"),
            Integer.parseInt(System.getenv().getOrDefault("OKAPI_TEST_CLICKHOUSE_PORT", "8123")),
            false)
        .setUsername("default")
        .setPassword("okapi_testing_password")
        .build();
  }
}
