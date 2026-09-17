/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
@ConfigurationProperties(prefix = "okapi.zk")
public class ZkCfg {
  @NotNull @NotBlank String connectString;

  @Min(value = 1000, message = "backOffMillis must be ≥ 1000ms")
  int backoffMillis;

  @Min(value = 2, message = "there should be at least 2 retries")
  int retries;
}
