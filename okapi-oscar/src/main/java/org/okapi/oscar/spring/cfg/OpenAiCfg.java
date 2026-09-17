/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring.cfg;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okapi.oscar.openai")
@Getter
@Setter
@NoArgsConstructor
public class OpenAiCfg {
  String baseUrl;
  String apiKeyPath;
  String model = "gpt-4o-mini";
}
