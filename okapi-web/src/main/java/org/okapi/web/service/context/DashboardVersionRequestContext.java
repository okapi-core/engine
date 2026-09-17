/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import static org.okapi.validation.OkapiChecks.checkArgument;

public record DashboardVersionRequestContext(String orgId, String dashboardId, String versionId) {
  public DashboardVersionRequestContext {
    checkArgument(orgId != null && !orgId.isBlank(), "Organization ID is required");
    checkArgument(dashboardId != null && !dashboardId.isBlank(), "Dashboard ID is required");
    checkArgument(versionId != null && !versionId.isBlank(), "Version ID is required");
  }
}
