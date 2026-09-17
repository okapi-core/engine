/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring.cfg;

import javax.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okapi.oscar.memory")
@Getter
@Setter
@NoArgsConstructor
public class MemoryCfg {
  @Min(value = 10, message = "Its recommended to use atleast 10 memory messages with Oscar.")
  int maxMessages;
}
