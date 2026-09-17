/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.dashboards.CreateDashboardRowRequest;
import org.okapi.web.dtos.dashboards.GetDashboardRowResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardRowRequest;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.okapi.web.service.dashboards.rows.DashboardRowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardRowController {
  @Autowired DashboardRowService rowService;

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows")
  public GetDashboardRowResponse createRow(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @RequestBody @Validated CreateDashboardRowRequest req)
      throws Exception {
    return rowService.create(
        new DashboardVersionRequestContext(orgId, dashboardId, versionId), req);
  }

  @GetMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}")
  public GetDashboardRowResponse getRow(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId)
      throws Exception {
    return rowService.read(new DashboardRowRequestContext(orgId, dashboardId, versionId, rowId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/delete")
  public void deleteRow(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId)
      throws Exception {
    rowService.delete(new DashboardRowRequestContext(orgId, dashboardId, versionId, rowId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/update")
  public GetDashboardRowResponse updateRow(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId,
      @RequestBody @Validated UpdateDashboardRowRequest req)
      throws Exception {
    return rowService.update(
        new DashboardRowRequestContext(orgId, dashboardId, versionId, rowId), req);
  }
}
