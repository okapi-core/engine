/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import java.util.List;
import org.okapi.web.dtos.dashboards.CreateDashboardRequest;
import org.okapi.web.dtos.dashboards.GetDashboardResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardRequest;
import org.okapi.web.dtos.dashboards.versions.ListDashboardVersionsResponse;
import org.okapi.web.dtos.dashboards.versions.PublishDashboardVersionRequest;
import org.okapi.web.dtos.dashboards.versions.PublishDashboardVersionResponse;
import org.okapi.web.service.context.DashboardRequestContext;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.okapi.web.service.context.OrgRequestContext;
import org.okapi.web.service.dashboards.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {
  @Autowired DashboardService dashboardService;

  @PostMapping("/orgs/{orgId}/dashboards")
  public GetDashboardResponse createDashboard(
      @PathVariable String orgId, @RequestBody @Validated CreateDashboardRequest req) {
    return dashboardService.create(new OrgRequestContext(orgId), req);
  }

  @GetMapping("/orgs/{orgId}/dashboards")
  public List<GetDashboardResponse> listDashboards(@PathVariable String orgId) throws Exception {
    return dashboardService.listDashboards(new OrgRequestContext(orgId));
  }

  @GetMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions")
  public ListDashboardVersionsResponse listVersions(
      @PathVariable String orgId, @PathVariable("dashboardId") String dashboardId)
      throws Exception {
    return dashboardService.listVersions(new DashboardRequestContext(orgId, dashboardId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/publish")
  public PublishDashboardVersionResponse publishDashboard(
      @PathVariable String orgId,
      @PathVariable("dashboardId") String dashboardId,
      @RequestBody @Validated PublishDashboardVersionRequest request)
      throws Exception {
    return dashboardService.publishVersion(
        new DashboardVersionRequestContext(orgId, dashboardId, request.getVersionId()));
  }

  @GetMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}")
  public GetDashboardResponse getDashboardVersion(
      @PathVariable String orgId,
      @PathVariable("dashboardId") String dashboardId,
      @PathVariable("versionId") String versionId)
      throws Exception {
    return dashboardService.readVersion(
        new DashboardVersionRequestContext(orgId, dashboardId, versionId));
  }

  @GetMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/active")
  public GetDashboardResponse getDashboardActiveVersion(
      @PathVariable String orgId, @PathVariable("dashboardId") String dashboardId) {
    return dashboardService.read(new DashboardRequestContext(orgId, dashboardId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/delete")
  public void deleteDashboard(
      @PathVariable String orgId, @PathVariable("dashboardId") String dashboardId)
      throws Exception {
    dashboardService.delete(new DashboardRequestContext(orgId, dashboardId));
  }

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/update")
  public GetDashboardResponse updateDashboard(
      @PathVariable String orgId,
      @PathVariable("dashboardId") String dashboardId,
      @RequestBody @Validated UpdateDashboardRequest req)
      throws Exception {
    return dashboardService.update(new DashboardRequestContext(orgId, dashboardId), req);
  }
}
