/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.dashboards.vars.CreateDashboardVarRequest;
import org.okapi.web.dtos.dashboards.vars.GetVarResponse;
import org.okapi.web.dtos.dashboards.vars.ListVarsResponse;
import org.okapi.web.service.context.DashboardRequestContext;
import org.okapi.web.service.dashboards.DashboardVarsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashVarController {

  @Autowired DashboardVarsService dashboardVarsService;

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/vars")
  public GetVarResponse createVar(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @RequestBody @Validated CreateDashboardVarRequest req)
      throws Exception {
    return dashboardVarsService.createVar(new DashboardRequestContext(orgId, dashboardId), req);
  }

  @GetMapping("/orgs/{orgId}/dashboards/{dashboardId}/vars")
  public ListVarsResponse listVars(@PathVariable String orgId, @PathVariable String dashboardId)
      throws Exception {
    return dashboardVarsService.listVars(new DashboardRequestContext(orgId, dashboardId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/vars/{name}/delete")
  public void deleteVar(
      @PathVariable String orgId, @PathVariable String dashboardId, @PathVariable String name)
      throws Exception {
    dashboardVarsService.deleteVar(new DashboardRequestContext(orgId, dashboardId), name);
  }
}
