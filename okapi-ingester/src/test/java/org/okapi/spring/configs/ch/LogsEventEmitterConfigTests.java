/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.logs.core.KafkaLogsEventEmitter;
import org.okapi.logs.core.LogsEventEmitter;
import org.okapi.logs.core.WalLogsEventEmitter;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.LogsConsumptionCfg;
import org.okapi.wal.manager.WalManager;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LogsEventEmitterConfigTests {
  @TempDir Path tempDir;

  @Test
  void wiresWalEmitterByDefault() {
    new ApplicationContextRunner()
        .withUserConfiguration(LogsEventEmitterConfig.class)
        .withBean(
            Qualifiers.LOGS_CH_WAL_RESOURCES,
            ChWalResources.class,
            () -> createWalResources(tempDir))
        .run(
            context ->
                assertThat(context.getBean(LogsEventEmitter.class))
                    .isInstanceOf(WalLogsEventEmitter.class));
  }

  @Test
  void wiresKafkaEmitterWhenConfigured() {
    var cfg = new LogsConsumptionCfg();
    cfg.setConsumptionType(LogsConsumptionCfg.ConsumptionType.KAFKA);
    cfg.getKafka().setBootstrapServers("localhost:9092");
    cfg.getKafka().setGroupId("logs-group");
    cfg.getKafka().setTopic("logs-topic");

    new ApplicationContextRunner()
        .withUserConfiguration(LogsEventEmitterConfig.class)
        .withPropertyValues("okapi.logs.consumptionType=kafka")
        .withBean(LogsConsumptionCfg.class, () -> cfg)
        .run(
            context ->
                assertThat(context.getBean(LogsEventEmitter.class))
                    .isInstanceOf(KafkaLogsEventEmitter.class));
  }

  private ChWalResources createWalResources(Path path) {
    try {
      return new ChWalResources(path, new WalManager.WalConfig(1024));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
