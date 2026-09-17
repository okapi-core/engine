/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.ch;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.spring.configs.Profiles;
import org.okapi.spring.configs.Qualifiers;
import org.okapi.spring.configs.properties.TracesConsumptionCfg;
import org.okapi.traces.core.KafkaTracesEventEmitter;
import org.okapi.traces.core.TracesEventEmitter;
import org.okapi.traces.core.WalTracesEventEmitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Profiles.PROFILE_CH)
public class TracesEventEmitterConfig {
  @Bean
  @ConditionalOnProperty(
      name = "okapi.traces.consumptionType",
      havingValue = "wal",
      matchIfMissing = true)
  public TracesEventEmitter walTracesEventEmitter(
      @Autowired @Qualifier(Qualifiers.TRACES_CH_WAL_RESOURCES) ChWalResources walResources) {
    return new WalTracesEventEmitter(walResources);
  }

  @Bean(destroyMethod = "close")
  @ConditionalOnProperty(name = "okapi.traces.consumptionType", havingValue = "kafka")
  public TracesEventEmitter kafkaTracesEventEmitter(@Autowired TracesConsumptionCfg cfg) {
    var kafkaCfg = cfg.getKafka();
    return new KafkaTracesEventEmitter(
        new KafkaConsumer<>(kafkaCfg.consumerProperties()),
        kafkaCfg.requiredTopic(),
        kafkaCfg.pollTimeout());
  }
}
