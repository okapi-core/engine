/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.data.model.Dashboard;
import org.okapi.web.service.dashboards.rows.DashboardRowId;

@AllArgsConstructor
@Getter
public class DashboardRowAccessContext {
  OrgMemberContext orgMemberContext;
  DashboardRowId rowId;
  Dashboard dashboardDdb;
  String versionId;
}
