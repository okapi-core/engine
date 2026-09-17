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
import java.util.Collection;
import org.okapi.engine.ch.ChLogQlParser;
import org.okapi.engine.ch.ChLogQlTranslator;
import org.okapi.engine.ch.ChLogsEngine;
import org.okapi.engine.ch.ChLogsTranslationBridge;
import org.okapi.engine.ch.ChQueryGenerator;
import org.okapi.logs.ch.ChLogsIngester;
import org.okapi.logs.ch.ChLogsQueryService;
import org.okapi.logs.ch.ChLogsSummaryService;
import org.okapi.logs.ch.ChLogsTemplateEngine;
import org.okapi.logs.ch.ChLogsWalConsumer;
import org.okapi.logs.ch.ChLogsWalConsumerDriver;
import org.okapi.logs.ch.OtelLogsToChRowsConverter;
import org.okapi.logs.core.FakeLogsEventEmitter;
import org.okapi.logs.core.LogsEvent;
import org.okapi.logs.core.LogsEventEmitter;
import org.okapi.metrics.ch.ChWriter;

public class TestChLogsModule extends AbstractModule {
  private final Collection<LogsEvent> events;
  private final int batchSize;

  public TestChLogsModule(Collection<LogsEvent> events, int batchSize) {
    this.events = events;
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
  FakeLogsEventEmitter provideFakeLogsEventEmitter() {
    return new FakeLogsEventEmitter(events);
  }

  @Provides
  @Singleton
  LogsEventEmitter provideLogsEventEmitter(FakeLogsEventEmitter emitter) {
    return emitter;
  }

  @Provides
  @Singleton
  OtelLogsToChRowsConverter provideOtelLogsToChRowsConverter() {
    return new OtelLogsToChRowsConverter();
  }

  @Provides
  @Singleton
  ChLogsWalConsumer provideChLogsWalConsumer(
      ChWriter writer, LogsEventEmitter eventEmitter, OtelLogsToChRowsConverter converter) {
    return new ChLogsWalConsumer(batchSize, writer, eventEmitter, converter);
  }

  @Provides
  @Singleton
  ChLogsWalConsumerDriver provideChLogsWalConsumerDriver(ChLogsWalConsumer consumer) {
    return new ChLogsWalConsumerDriver(consumer);
  }

  @Provides
  @Singleton
  ChLogsTemplateEngine provideChLogsTemplateEngine() {
    return new ChLogsTemplateEngine();
  }

  @Provides
  @Singleton
  ChLogsQueryService provideChLogsQueryService(Client client, ChLogsTemplateEngine templateEngine) {
    return new ChLogsQueryService(client, templateEngine);
  }

  @Provides
  @Singleton
  ChLogsSummaryService provideChLogsSummaryService(Client client) {
    return new ChLogsSummaryService(client);
  }

  @Provides
  @Singleton
  ChLogsIngester provideChLogsIngester() {
    return new ChLogsIngester(null, false);
  }

  @Provides
  @Singleton
  ChLogsTranslationBridge provideChLogsTranslationBridge() {
    return new ChLogsTranslationBridge();
  }

  @Provides
  @Singleton
  ChLogQlTranslator provideChLogQlTranslator(ChLogsTranslationBridge bridge) {
    return new ChLogQlTranslator(bridge);
  }

  @Provides
  @Singleton
  ChLogQlParser provideChLogQlParser() {
    return new ChLogQlParser();
  }

  @Provides
  @Singleton
  ChQueryGenerator provideChQueryGenerator() {
    return new ChQueryGenerator();
  }

  @Provides
  @Singleton
  ChLogsEngine provideChLogsEngine(
      ChLogsQueryService queryService,
      ChLogsSummaryService summaryService,
      ChLogsIngester ingester,
      ChLogQlTranslator translator,
      ChLogQlParser parser,
      ChQueryGenerator generator,
      Client client) {
    return new ChLogsEngine(
        queryService, summaryService, ingester, translator, parser, generator, client);
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
