/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import static org.okapi.validation.OkapiChecks.checkArgument;

public record DashboardRowRequestContext(
    String orgId, String dashboardId, String versionId, String rowId) {
  public DashboardRowRequestContext {
    checkArgument(orgId != null && !orgId.isBlank(), "Organization ID is required");
    checkArgument(dashboardId != null && !dashboardId.isBlank(), "Dashboard ID is required");
    checkArgument(versionId != null && !versionId.isBlank(), "Version ID is required");
    checkArgument(rowId != null && !rowId.isBlank(), "Row ID is required");
  }
}
