/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.yaml;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class BulkApplyDashboardYamlResponse {
  boolean ok;
  String status;
  List<ApplyDashboardYamlResponse> imported;
  List<BulkDashboardYamlLintIssue> errors;
  List<BulkDashboardYamlLintIssue> warnings;
}
