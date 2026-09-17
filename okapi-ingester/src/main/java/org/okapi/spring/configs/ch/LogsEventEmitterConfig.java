/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.okapi.logs.core.KafkaLogsEventEmitter;
import org.okapi.logs.core.LogsEventEmitter;
import org.okapi.logs.core.WalLogsEventEmitter;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.LogsConsumptionCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_CH)
public class LogsEventEmitterConfig {
  @Bean
  @ConditionalOnProperty(
      name = "okapi.logs.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public LogsEventEmitter walLogsEventEmitter(
      @Autowired @Qualifier(Qualifiers.LOGS_CH_WAL_RESOURCES) ChWalResources walResources) {
    return new WalLogsEventEmitter(walResources);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "okapi.logs.consumptionType", havingValue = "kafka")
  public LogsEventEmitter kafkaLogsEventEmitter(@Autowired LogsConsumptionCfg cfg) {
    var kafkaCfg = cfg.getKafka();
    return new KafkaLogsEventEmitter(
        new KafkaConsumer<>(kafkaCfg.consumerProperties()),
        kafkaCfg.requiredTopic(),
        kafkaCfg.pollTimeout());
  }
}
