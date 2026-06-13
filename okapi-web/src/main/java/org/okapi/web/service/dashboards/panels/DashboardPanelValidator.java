/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import lombok.AllArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.Dashboard;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.dtos.dashboards.CreateDashboardPanelRequest;
import org.okapi.web.dtos.dashboards.UpdateDashboardPanelRequest;
import org.okapi.web.service.ProtectedResourceContext;
import org.okapi.web.service.context.DashboardAccessContext;
import org.okapi.web.service.dashboards.DashboardAccessValidator;
import org.okapi.web.service.validation.OrgMemberValidator;
import org.okapi.web.service.validation.RequestValidator;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DashboardPanelValidator
    implements RequestValidator<
        ProtectedResourceContext,
        DashboardPanelContext,
        CreateDashboardPanelRequest,
        UpdateDashboardPanelRequest> {
  OrgMemberValidator orgMemberValidator;
  DashboardAccessValidator dashboardAccessValidator;
  DashboardPanelIdParser dashboardPanelIdParser;
  DashboardDao dashboardDao;
  DashboardVersionDao dashboardVersionDao;

  @Override
  public DashboardPanelContext validateCreate(
      ProtectedResourceContext context, CreateDashboardPanelRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var orgCtx = orgMemberValidator.checkOrgMember(context.getToken());
    var dashboardCtx = new DashboardAccessContext(context.getToken(), request.getDashboardId());
    dashboardAccessValidator.validateEdit(dashboardCtx);
    var dashboard = getDashboardOrThrow(orgCtx.getOrgId(), request.getDashboardId());
    validateVersionExists(orgCtx.getOrgId(), request.getDashboardId(), request.getVersionId());
    return DashboardPanelContext.of(orgCtx, dashboard, request.getVersionId());
  }

  @Override
  public DashboardPanelContext validateRead(ProtectedResourceContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkExists(context);
    var panelId = dashboardPanelIdParser.parse(context.getResourceIdOrThrow());
    var orgCtx =
        dashboardAccessValidator.validateRead(
            new DashboardAccessContext(context.getToken(), panelId.getDashboardId()));
    var dashboard = getDashboardOrThrow(orgCtx.getOrgId(), panelId.getDashboardId());
    var versionId = context.getVersionIdOrThrow();
    validateVersionExists(orgCtx.getOrgId(), panelId.getDashboardId(), versionId);
    return new DashboardPanelContext(orgCtx, panelId, dashboard, versionId);
  }

  @Override
  public DashboardPanelContext validateUpdate(
      ProtectedResourceContext context, UpdateDashboardPanelRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkExists(context);
    var dashboardCtx = new DashboardAccessContext(context.getToken(), request.getDashboardId());
    var orgCtx = dashboardAccessValidator.validateEdit(dashboardCtx);
    var panelId = dashboardPanelIdParser.parse(context.getResourceIdOrThrow());
    var dashboard = getDashboardOrThrow(orgCtx.getOrgId(), request.getDashboardId());
    validateVersionExists(orgCtx.getOrgId(), request.getDashboardId(), request.getVersionId());
    return new DashboardPanelContext(orgCtx, panelId, dashboard, request.getVersionId());
  }

  @Override
  public DashboardPanelContext validateDelete(ProtectedResourceContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    checkExists(context);
    var parsed = DashboardPanelIdParser.parseStatic(context.getResourceIdOrThrow());
    var dashboardCtx = new DashboardAccessContext(context.getToken(), parsed.getDashboardId());
    var orgCtx = dashboardAccessValidator.validateEdit(dashboardCtx);
    var dashboard = getDashboardOrThrow(orgCtx.getOrgId(), parsed.getDashboardId());
    var versionId = context.getVersionIdOrThrow();
    validateVersionExists(orgCtx.getOrgId(), parsed.getDashboardId(), versionId);
    return new DashboardPanelContext(orgCtx, parsed, dashboard, versionId);
  }

  public void checkExists(ProtectedResourceContext context) throws ResourceNotFoundException {
    if (context.getToken().isEmpty()) {
      throw new ResourceNotFoundException("Dashboard panel not found");
    }
  }

  private Dashboard getDashboardOrThrow(String orgId, String dashboardId)
      throws ResourceNotFoundException {
    var dashboard = dashboardDao.get(orgId, dashboardId);
    if (dashboard.isEmpty()) {
      throw new ResourceNotFoundException("Dashboard not found: " + dashboardId);
    }
    return dashboard.get();
  }

  private void validateVersionExists(String orgId, String dashboardId, String versionId)
      throws ResourceNotFoundException {
    if (versionId == null || versionId.isBlank()) {
      throw new ResourceNotFoundException("Version not found");
    }
    if (dashboardVersionDao.get(orgId, dashboardId, versionId).isEmpty()) {
      throw new ResourceNotFoundException("Version not found: " + versionId);
    }
  }
}
