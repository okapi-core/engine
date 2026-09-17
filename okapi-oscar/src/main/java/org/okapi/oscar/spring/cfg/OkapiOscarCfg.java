/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring.cfg;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okapi.oscar.model")
@Getter
@Setter
@NoArgsConstructor
public class OkapiOscarCfg {
  private String coreModelProvider;
  private String systemPrompt;
}
