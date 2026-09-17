/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.bootstrap;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "okapi.org")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class OkapiOrgProperties {
  String orgId;
  String orgName;
}
