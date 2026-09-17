/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.context.DashboardPanelRequestContext;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardPanelValidator {
  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;
  private final DashboardDao dashboardDao;
  private final DashboardVersionDao dashboardVersionDao;
  private final DashboardRowDao dashboardRowDao;

  public void validate(DashboardRowRequestContext context) {
    validateHierarchy(context.orgId(), context.dashboardId(), context.versionId(), context.rowId());
  }

  public void validate(DashboardPanelRequestContext context) {
    validateHierarchy(context.orgId(), context.dashboardId(), context.versionId(), context.rowId());
  }

  private void validateHierarchy(String orgId, String dashboardId, String versionId, String rowId) {
    accessManager.checkOrgMember(currentUserProvider.userId(), orgId);
    if (dashboardDao.get(orgId, dashboardId).isEmpty()) {
      throw new ResourceNotFoundException("Dashboard not found: " + dashboardId);
    }
    if (dashboardVersionDao.get(orgId, dashboardId, versionId).isEmpty()) {
      throw new ResourceNotFoundException("Version not found: " + versionId);
    }
    if (dashboardRowDao.get(orgId, dashboardId, versionId, rowId).isEmpty()) {
      throw new ResourceNotFoundException("Row not found: " + rowId);
    }
  }
}
