/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.rows;

import lombok.AllArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.Dashboard;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.dtos.dashboards.CreateDashboardRowRequest;
import org.okapi.web.dtos.dashboards.UpdateDashboardRowRequest;
import org.okapi.web.service.ProtectedResourceContext;
import org.okapi.web.service.context.DashboardAccessContext;
import org.okapi.web.service.context.DashboardRowAccessContext;
import org.okapi.web.service.dashboards.DashboardAccessValidator;
import org.okapi.web.service.validation.OrgMemberValidator;
import org.okapi.web.service.validation.RequestValidator;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DashboardRowValidator
    implements RequestValidator<
        ProtectedResourceContext,
        DashboardRowAccessContext,
        CreateDashboardRowRequest,
        UpdateDashboardRowRequest> {

  OrgMemberValidator orgMemberValidator;
  DashboardAccessValidator dashboardAccessValidator;
  DashboardDao dashboardDao;
  DashboardRowDao dashboardRowDao;
  DashboardVersionDao dashboardVersionDao;

  @Override
  public DashboardRowAccessContext validateCreate(
      ProtectedResourceContext context, CreateDashboardRowRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    orgMemberValidator.checkOrgMatch(context.getToken(), request.getOrgId());
    var orgMemberContext = orgMemberValidator.checkOrgMember(context.getToken());
    var dashboard = getDashboardOrThrow(request.getOrgId(), request.getDashboardId());
    validateVersionExists(request.getOrgId(), request.getDashboardId(), request.getVersionId());
    return new DashboardRowAccessContext(orgMemberContext, null, dashboard, request.getVersionId());
  }

  @Override
  public DashboardRowAccessContext validateRead(ProtectedResourceContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var parsedId = DashboardRowIdParser.parse(context.getResourceIdOrThrow());
    var orgCtx =
        dashboardAccessValidator.validateRead(
            new DashboardAccessContext(context.getToken(), parsedId.getDashboardId()));
    var dashboard = getDashboardOrThrow(parsedId);
    var versionId = context.getVersionIdOrThrow();
    validateVersionExists(parsedId.getOrgId(), parsedId.getDashboardId(), versionId);
    return new DashboardRowAccessContext(orgCtx, parsedId, dashboard, versionId);
  }

  @Override
  public DashboardRowAccessContext validateUpdate(
      ProtectedResourceContext context, UpdateDashboardRowRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var parsedId = DashboardRowIdParser.parse(context.getResourceIdOrThrow());
    var orgCtx =
        dashboardAccessValidator.validateEdit(
            new DashboardAccessContext(context.getToken(), request.getDashboardId()));
    var dashboard = getDashboardOrThrow(parsedId);
    validateVersionExists(parsedId.getOrgId(), parsedId.getDashboardId(), request.getVersionId());
    return new DashboardRowAccessContext(orgCtx, parsedId, dashboard, request.getVersionId());
  }

  @Override
  public DashboardRowAccessContext validateDelete(ProtectedResourceContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var parsedId = DashboardRowIdParser.parse(context.getResourceIdOrThrow());
    var orgCtx =
        dashboardAccessValidator.validateEdit(
            new DashboardAccessContext(context.getToken(), parsedId.getDashboardId()));
    var dashboard = getDashboardOrThrow(parsedId);
    var versionId = context.getVersionIdOrThrow();
    validateVersionExists(parsedId.getOrgId(), parsedId.getDashboardId(), versionId);
    return new DashboardRowAccessContext(orgCtx, parsedId, dashboard, versionId);
  }

  public Dashboard getDashboardOrThrow(DashboardRowId rowId) throws ResourceNotFoundException {
    return getDashboardOrThrow(rowId.getOrgId(), rowId.getDashboardId());
  }

  public Dashboard getDashboardOrThrow(String orgId, String dashboardId)
      throws ResourceNotFoundException {
    var dashboardDdb = dashboardDao.get(orgId, dashboardId);
    if (dashboardDdb.isEmpty()) {
      throw new ResourceNotFoundException("Dashboard not found: " + dashboardId);
    }
    return dashboardDdb.get();
  }

  public DashboardRow getRowOrThrow(DashboardRowId rowId) throws ResourceNotFoundException {
    var dashboardDdb = getDashboardOrThrow(rowId);
    var row =
        dashboardRowDao.get(
            rowId.getOrgId(),
            rowId.getDashboardId(),
            dashboardDdb.getActiveVersion(),
            rowId.getRowId());
    if (row.isEmpty()) {
      throw new ResourceNotFoundException("Row not found: " + rowId.getRowId());
    }
    return row.get();
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
