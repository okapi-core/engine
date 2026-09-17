/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.dashboards.CreateDashboardPanelRequest;
import org.okapi.web.dtos.dashboards.GetDashboardPanelResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardPanelRequest;
import org.okapi.web.service.context.DashboardPanelRequestContext;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.okapi.web.service.dashboards.panels.DashboardPanelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class DashboardPanelController {
  @Autowired DashboardPanelService panelService;

  @PostMapping("/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/panels")
  public GetDashboardPanelResponse createPanel(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId,
      @RequestBody @Validated CreateDashboardPanelRequest req) {
    return panelService.create(
        new DashboardRowRequestContext(orgId, dashboardId, versionId, rowId), req);
  }

  @GetMapping(
      "/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/panels/{panelId}")
  public GetDashboardPanelResponse getPanel(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId,
      @PathVariable String panelId)
      throws Exception {
    return panelService.read(
        new DashboardPanelRequestContext(orgId, dashboardId, versionId, rowId, panelId));
  }

  @PostMapping(
      "/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/panels/{panelId}/delete")
  public void deletePanel(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId,
      @PathVariable String panelId)
      throws Exception {
    panelService.delete(
        new DashboardPanelRequestContext(orgId, dashboardId, versionId, rowId, panelId));
  }

  @PostMapping(
      "/orgs/{orgId}/dashboards/{dashboardId}/versions/{versionId}/rows/{rowId}/panels/{panelId}/update")
  public GetDashboardPanelResponse updatePanel(
      @PathVariable String orgId,
      @PathVariable String dashboardId,
      @PathVariable String versionId,
      @PathVariable String rowId,
      @PathVariable String panelId,
      @RequestBody @Validated UpdateDashboardPanelRequest req)
      throws Exception {
    return panelService.update(
        new DashboardPanelRequestContext(orgId, dashboardId, versionId, rowId, panelId), req);
  }
}
