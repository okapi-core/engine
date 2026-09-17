/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.dtos.dashboards.CreateDashboardRequest;
import org.okapi.web.dtos.dashboards.UpdateDashboardRequest;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.context.DashboardRequestContext;
import org.okapi.web.service.context.OrgRequestContext;
import org.springframework.stereotype.Service;

@Service
public class DashboardsRequestValidator {

  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;

  public DashboardsRequestValidator(
      AccessManager accessManager, CurrentUserProvider currentUserProvider) {
    this.accessManager = accessManager;
    this.currentUserProvider = currentUserProvider;
  }

  public void validateCreate(OrgRequestContext context, CreateDashboardRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkOrgAccess(context.orgId());
  }

  public void validateList(OrgRequestContext context)
      throws UnAuthorizedException, ResourceNotFoundException {
    checkOrgAccess(context.orgId());
  }

  public void validateRead(DashboardRequestContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkResourceExists(context);
    checkOrgAccess(context.orgId());
  }

  public void validateUpdate(DashboardRequestContext context, UpdateDashboardRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkResourceExists(context);
    checkOrgAccess(context.orgId());
  }

  public void validateDelete(DashboardRequestContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkResourceExists(context);
    checkOrgAccess(context.orgId());
  }

  public void checkResourceExists(DashboardRequestContext context)
      throws ResourceNotFoundException {
    if (context.dashboardId().isBlank()) {
      throw new ResourceNotFoundException("Resource not found.");
    }
  }

  private void checkOrgAccess(String orgId) {
    accessManager.checkOrgMember(currentUserProvider.userId(), orgId);
  }
}
