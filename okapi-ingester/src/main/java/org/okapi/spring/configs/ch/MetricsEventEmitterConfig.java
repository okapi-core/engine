/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.metrics.core.KafkaMetricsEventEmitter;
import org.okapi.metrics.core.MetricsEventEmitter;
import org.okapi.metrics.core.WalMetricsEventEmitter;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.MetricsConsumptionCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_CH)
public class MetricsEventEmitterConfig {
  @Bean
  @ConditionalOnProperty(
      name = "okapi.metrics.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public MetricsEventEmitter walMetricsEventEmitter(
      @Autowired @Qualifier(Qualifiers.METRICS_CH_WAL_RESOURCES) ChWalResources walResources) {
    return new WalMetricsEventEmitter(walResources);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "okapi.metrics.consumptionType", havingValue = "kafka")
  public MetricsEventEmitter kafkaMetricsEventEmitter(@Autowired MetricsConsumptionCfg cfg) {
    var kafkaCfg = cfg.getKafka();
    return new KafkaMetricsEventEmitter(
        new KafkaConsumer<>(kafkaCfg.consumerProperties()),
        kafkaCfg.requiredTopic(),
        kafkaCfg.pollTimeout());
  }
}
