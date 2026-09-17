/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "okapi.ch.wal")
@Component
@Validated
public class ChWalConsumerCfg {
  @Min(value = 1, message = "consumeIntervalMs must be >= 1")
  long consumeIntervalMs = 200;

  @Min(value = 1, message = "batchSize must be >= 1")
  int batchSize = 1024;
}
