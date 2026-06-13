/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import lombok.AllArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.model.DashboardVariable;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.validation.OkapiChecks;
import org.okapi.web.dtos.dashboards.vars.CreateDashboardVarRequest;
import org.okapi.web.dtos.dashboards.vars.DASH_VAR_TYPE;
import org.okapi.web.dtos.dashboards.vars.DeleteDashboardVarRequest;
import org.okapi.web.dtos.dashboards.vars.GetVarResponse;
import org.okapi.web.dtos.dashboards.vars.ListVarsResponse;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.access.OrgMemberChecker;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DashboardVarsService {
  OrgMemberChecker orgMemberChecker;
  DashboardDao dashboardDao;
  DashboardVarDao dashboardVarDao;

  public GetVarResponse createVar(String tempToken, CreateDashboardVarRequest request) {
    var ctx = orgMemberChecker.checkUserIsOrgMember(tempToken);
    var boardOpt = dashboardDao.get(ctx.getOrgId(), request.getDashboardId());
    OkapiChecks.checkArgument(boardOpt.isPresent(), ResourceNotFoundException::new);
    var versionId = boardOpt.get().getActiveVersion();
    var varType = toDashboardVarType(request.getDashVarType());
    var dashVar =
        DashboardVariable.builder()
            .varName(request.getName())
            .tag(request.getTag())
            .varType(varType)
            .build();
    dashboardVarDao.save(ctx.getOrgId(), request.getDashboardId(), versionId, dashVar);
    return Mappers.mapDashboardVarToResponse(dashVar);
  }

  public void deleteVar(String tempToken, DeleteDashboardVarRequest request) {
    var ctx = orgMemberChecker.checkUserIsOrgMember(tempToken);
    var boardOpt = dashboardDao.get(ctx.getOrgId(), request.getDashboardId());
    OkapiChecks.checkArgument(boardOpt.isPresent(), ResourceNotFoundException::new);
    var versionId = boardOpt.get().getActiveVersion();
    dashboardVarDao.delete(ctx.getOrgId(), request.getDashboardId(), versionId, request.getName());
  }

  public ListVarsResponse listVar(String tempToken, String dashboardId) {
    var ctx = orgMemberChecker.checkUserIsOrgMember(tempToken);
    var boardOpt = dashboardDao.get(ctx.getOrgId(), dashboardId);
    OkapiChecks.checkArgument(boardOpt.isPresent(), ResourceNotFoundException::new);
    var versionId = boardOpt.get().getActiveVersion();
    var vars =
        dashboardVarDao.list(ctx.getOrgId(), dashboardId, versionId).stream()
            .map(Mappers::mapDashboardVarToResponse)
            .toList();
    return ListVarsResponse.builder().vars(vars).build();
  }

  private static DashboardVariable.Type toDashboardVarType(DASH_VAR_TYPE type) {
    if (type == null) return null;
    return switch (type) {
      case METRIC -> DashboardVariable.Type.METRIC;
      case TAG_VALUE -> DashboardVariable.Type.TAG;
    };
  }
}
