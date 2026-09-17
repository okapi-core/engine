/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;

class MetricsConsumptionCfgTests {
  @Test
  void mapsKafkaConsumerProperties() {
    var kafka = new MetricsConsumptionCfg.Kafka();
    kafka.setBootstrapServers("kafka:9092");
    kafka.setGroupId("metrics-group");
    kafka.setTopic("metrics-topic");
    kafka.setMaxPollRecords(12);

    var properties = kafka.consumerProperties();

    assertEquals("kafka:9092", properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    assertEquals("metrics-group", properties.get(ConsumerConfig.GROUP_ID_CONFIG));
    assertEquals(false, properties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
    assertEquals(12, properties.get(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
    assertEquals("metrics-topic", kafka.requiredTopic());
  }

  @Test
  void rejectsMissingKafkaTopic() {
    var kafka = new MetricsConsumptionCfg.Kafka();

    var error = assertThrows(IllegalArgumentException.class, kafka::requiredTopic);

    assertEquals("okapi.metrics.kafka.topic must be configured", error.getMessage());
  }
}
