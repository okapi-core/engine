/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.TracesConsumptionCfg;
import org.okapi.traces.core.KafkaTracesEventEmitter;
import org.okapi.traces.core.TracesEventEmitter;
import org.okapi.traces.core.WalTracesEventEmitter;
import org.okapi.wal.manager.WalManager;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TracesEventEmitterConfigTests {
  @TempDir Path tempDir;

  @Test
  void wiresWalEmitterByDefault() {
    new ApplicationContextRunner()
        .withUserConfiguration(TracesEventEmitterConfig.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(Profiles.PROFILE_CH))
        .withBean(
            Qualifiers.TRACES_CH_WAL_RESOURCES,
            ChWalResources.class,
            () -> createWalResources(tempDir))
        .run(
            context ->
                assertThat(context.getBean(TracesEventEmitter.class))
                    .isInstanceOf(WalTracesEventEmitter.class));
  }

  @Test
  void wiresKafkaEmitterWhenConfigured() {
    var cfg = new TracesConsumptionCfg();
    cfg.setConsumptionType(TracesConsumptionCfg.ConsumptionType.KAFKA);
    cfg.getKafka().setBootstrapServers("localhost:9092");
    cfg.getKafka().setGroupId("traces-group");
    cfg.getKafka().setTopic("traces-topic");

    new ApplicationContextRunner()
        .withUserConfiguration(TracesEventEmitterConfig.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(Profiles.PROFILE_CH))
        .withPropertyValues("okapi.traces.consumptionType=kafka")
        .withBean(TracesConsumptionCfg.class, () -> cfg)
        .run(
            context ->
                assertThat(context.getBean(TracesEventEmitter.class))
                    .isInstanceOf(KafkaTracesEventEmitter.class));
  }

  private ChWalResources createWalResources(Path path) {
    try {
      return new ChWalResources(path, new WalManager.WalConfig(1024));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
