/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring.cfg;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okapi.oscar.vault")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class VaultCfg {
  String address;
  String token;
}
