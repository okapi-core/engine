/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.versions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetDashboardVersionResponse {
  String dashboardId;
  String versionId;
  String status;
  long createdAt;
  String createdBy;
  String note;
  String specHash;
}
