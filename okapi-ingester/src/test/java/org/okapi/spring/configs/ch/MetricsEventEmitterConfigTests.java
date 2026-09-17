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
import org.okapi.metrics.core.KafkaMetricsEventEmitter;
import org.okapi.metrics.core.MetricsEventEmitter;
import org.okapi.metrics.core.WalMetricsEventEmitter;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.MetricsConsumptionCfg;
import org.okapi.wal.manager.WalManager;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MetricsEventEmitterConfigTests {
  @TempDir Path tempDir;

  @Test
  void wiresWalEmitterByDefault() {
    new ApplicationContextRunner()
        .withUserConfiguration(MetricsEventEmitterConfig.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(Profiles.PROFILE_CH))
        .withBean(
            Qualifiers.METRICS_CH_WAL_RESOURCES,
            ChWalResources.class,
            () -> createWalResources(tempDir))
        .run(
            context ->
                assertThat(context.getBean(MetricsEventEmitter.class))
                    .isInstanceOf(WalMetricsEventEmitter.class));
  }

  @Test
  void wiresKafkaEmitterWhenConfigured() {
    var cfg = new MetricsConsumptionCfg();
    cfg.setConsumptionType(MetricsConsumptionCfg.ConsumptionType.KAFKA);
    cfg.getKafka().setBootstrapServers("localhost:9092");
    cfg.getKafka().setGroupId("metrics-group");
    cfg.getKafka().setTopic("metrics-topic");

    new ApplicationContextRunner()
        .withUserConfiguration(MetricsEventEmitterConfig.class)
        .withInitializer(context -> context.getEnvironment().setActiveProfiles(Profiles.PROFILE_CH))
        .withPropertyValues("okapi.metrics.consumptionType=kafka")
        .withBean(MetricsConsumptionCfg.class, () -> cfg)
        .run(
            context ->
                assertThat(context.getBean(MetricsEventEmitter.class))
                    .isInstanceOf(KafkaMetricsEventEmitter.class));
  }

  private ChWalResources createWalResources(Path path) {
    try {
      return new ChWalResources(path, new WalManager.WalConfig(1024));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
