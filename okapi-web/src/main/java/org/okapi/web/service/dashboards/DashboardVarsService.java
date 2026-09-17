/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.DashboardVariable;
import org.okapi.validation.OkapiChecks;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.dtos.dashboards.vars.CreateDashboardVarRequest;
import org.okapi.web.dtos.dashboards.vars.DASH_VAR_TYPE;
import org.okapi.web.dtos.dashboards.vars.GetVarResponse;
import org.okapi.web.dtos.dashboards.vars.ListVarsResponse;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.context.DashboardRequestContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardVarsService {
  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;
  private final DashboardDao dashboardDao;
  private final DashboardVarDao dashboardVarDao;

  public GetVarResponse createVar(
      DashboardRequestContext context, CreateDashboardVarRequest request) {
    var versionId = activeVersion(context);
    var dashVar =
        DashboardVariable.builder()
            .varName(request.getName())
            .tag(request.getTag())
            .varType(toDashboardVarType(request.getDashVarType()))
            .build();
    dashboardVarDao.save(context.orgId(), context.dashboardId(), versionId, dashVar);
    return Mappers.mapDashboardVarToResponse(dashVar);
  }

  public void deleteVar(DashboardRequestContext context, String name) {
    var versionId = activeVersion(context);
    dashboardVarDao.delete(context.orgId(), context.dashboardId(), versionId, name);
  }

  public ListVarsResponse listVars(DashboardRequestContext context) {
    var versionId = activeVersion(context);
    var vars =
        dashboardVarDao.list(context.orgId(), context.dashboardId(), versionId).stream()
            .map(Mappers::mapDashboardVarToResponse)
            .toList();
    return ListVarsResponse.builder().vars(vars).build();
  }

  private String activeVersion(DashboardRequestContext context) {
    accessManager.checkOrgMember(currentUserProvider.userId(), context.orgId());
    var dashboard = dashboardDao.get(context.orgId(), context.dashboardId());
    OkapiChecks.checkArgument(dashboard.isPresent(), ResourceNotFoundException::new);
    return dashboard.get().getActiveVersion();
  }

  private static DashboardVariable.Type toDashboardVarType(DASH_VAR_TYPE type) {
    if (type == null) return null;
    return switch (type) {
      case METRIC -> DashboardVariable.Type.METRIC;
      case TAG_VALUE -> DashboardVariable.Type.TAG;
    };
  }
}
