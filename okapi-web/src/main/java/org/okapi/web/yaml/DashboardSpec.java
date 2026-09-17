/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.yaml;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class DashboardSpec {
  String id;
  String title;
  String description;
  List<String> tags;
  List<DashboardVarSpec> vars;
  List<DashboardRowSpec> rows;
}
