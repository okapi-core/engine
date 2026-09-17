/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.yaml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class DashboardVarSpec {
  String name;
  String type;
  String tag;
}
