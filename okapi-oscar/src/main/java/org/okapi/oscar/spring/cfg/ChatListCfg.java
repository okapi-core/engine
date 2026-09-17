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

@ConfigurationProperties(prefix = "okapi.oscar.chat-list")
@Getter
@Setter
@NoArgsConstructor
public class ChatListCfg {
  @Min(1)
  int defaultLimit = 20;

  @Min(1)
  int maxLimit = 100;
}
