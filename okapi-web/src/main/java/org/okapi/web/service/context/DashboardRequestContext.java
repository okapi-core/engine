/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.context;

import static org.okapi.validation.OkapiChecks.checkArgument;

public record DashboardRequestContext(String orgId, String dashboardId) {
  public DashboardRequestContext {
    checkArgument(orgId != null && !orgId.isBlank(), "Organization ID is required");
    checkArgument(dashboardId != null && !dashboardId.isBlank(), "Dashboard ID is required");
  }
}
