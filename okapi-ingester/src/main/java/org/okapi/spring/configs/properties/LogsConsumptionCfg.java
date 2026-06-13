/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import java.time.Duration;
import java.util.Properties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@Component
@ConfigurationProperties(prefix = "okapi.logs")
public class LogsConsumptionCfg {
  private ConsumptionType consumptionType = ConsumptionType.WAL;
  private Kafka kafka = new Kafka();

  public enum ConsumptionType {
    WAL,
    KAFKA
  }

  @Data
  @NoArgsConstructor
  public static class Kafka {
    private String bootstrapServers;
    private String topic;
    private String groupId;
    private long pollTimeoutMs = 100;
    private int maxPollRecords = 1024;

    public Duration pollTimeout() {
      return Duration.ofMillis(pollTimeoutMs);
    }

    public Properties consumerProperties() {
      var properties = new Properties();
      properties.put(
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, required(bootstrapServers, "bootstrapServers"));
      properties.put(ConsumerConfig.GROUP_ID_CONFIG, required(groupId, "groupId"));
      properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
      properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
      properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
      return properties;
    }

    public String requiredTopic() {
      return required(topic, "topic");
    }

    private String required(String value, String key) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("okapi.logs.kafka." + key + " must be configured");
      }
      return value;
    }
  }
}
