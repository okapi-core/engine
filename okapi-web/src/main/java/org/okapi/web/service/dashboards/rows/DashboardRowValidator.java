/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.rows;

import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardRowValidator {
  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;
  private final DashboardDao dashboardDao;
  private final DashboardVersionDao dashboardVersionDao;

  public void validate(DashboardVersionRequestContext context) {
    validateHierarchy(context.orgId(), context.dashboardId(), context.versionId());
  }

  public void validate(DashboardRowRequestContext context) {
    validateHierarchy(context.orgId(), context.dashboardId(), context.versionId());
  }

  private void validateHierarchy(String orgId, String dashboardId, String versionId) {
    accessManager.checkOrgMember(currentUserProvider.userId(), orgId);
    if (dashboardDao.get(orgId, dashboardId).isEmpty()) {
      throw new ResourceNotFoundException("Dashboard not found: " + dashboardId);
    }
    if (dashboardVersionDao.get(orgId, dashboardId, versionId).isEmpty()) {
      throw new ResourceNotFoundException("Version not found: " + versionId);
    }
  }
}
