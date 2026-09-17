/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs.properties;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "okapi.promql.query")
@Component
@Validated
public class PromQlQueryCfg {

  @Min(value = 1, message = "evalThreads must be > 0")
  int evalThreads = 2;
}
