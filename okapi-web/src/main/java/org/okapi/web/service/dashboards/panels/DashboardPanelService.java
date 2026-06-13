/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import static org.okapi.validation.OkapiChecks.checkArgument;
import static org.okapi.web.service.Mappers.mapPanelQueryConfig;

import java.util.UUID;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.model.DashboardPanel;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.web.dtos.dashboards.CreateDashboardPanelRequest;
import org.okapi.web.dtos.dashboards.GetDashboardPanelResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardPanelRequest;
import org.okapi.web.service.AbstractValidatedCrudService;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.ProtectedResourceContext;
import org.springframework.stereotype.Service;

@Service
public class DashboardPanelService
    extends AbstractValidatedCrudService<
        ProtectedResourceContext,
        DashboardPanelContext,
        CreateDashboardPanelRequest,
        UpdateDashboardPanelRequest,
        GetDashboardPanelResponse> {

  private final DashboardPanelDao panelDao;
  private final DashboardRowDao dashboardRowDao;

  public DashboardPanelService(
      DashboardPanelValidator validator,
      DashboardPanelDao panelDao,
      DashboardRowDao dashboardRowDao) {
    super(validator);
    this.panelDao = panelDao;
    this.dashboardRowDao = dashboardRowDao;
  }

  @Override
  public GetDashboardPanelResponse createAfterValidation(
      DashboardPanelContext panelContext, CreateDashboardPanelRequest request) {
    var panelId =
        request.getPanelId() != null ? request.getPanelId() : UUID.randomUUID().toString();
    var versionId = panelContext.getVersionId();
    var panel =
        DashboardPanel.builder()
            .panelId(panelId)
            .title(request.getTitle())
            .note(request.getNote())
            .build();
    if (request.getQueryConfig() != null) {
      panel.setQueryConfig(mapPanelQueryConfig(request.getQueryConfig()));
    }
    panelDao.save(
        request.getOrgId(), request.getDashboardId(), request.getRowId(), versionId, panel);

    // update drawing order in the row
    var row =
        dashboardRowDao.get(
            request.getOrgId(), request.getDashboardId(), versionId, request.getRowId());
    if (row.isPresent()) {
      var fetchedRow = row.get();
      var order = fetchedRow.getPanelOrder();
      if (order == null) {
        order = new ResourceOrder();
      }
      order.add(panelId);
      fetchedRow.setPanelOrder(order);
      dashboardRowDao.save(request.getOrgId(), request.getDashboardId(), versionId, fetchedRow);
    }
    return Mappers.mapDashboardPanelToResponse(panel);
  }

  @Override
  public GetDashboardPanelResponse readAfterValidation(DashboardPanelContext panelContext)
      throws ResourceNotFoundException {
    var parts = parsePanelId(panelContext.getPanelFqId().get());
    var orgId = parts[0];
    var dashboardId = parts[1];
    var rowId = parts[2];
    var panelId = parts[3];
    var versionId = panelContext.getVersionId();
    var panelOpt = panelDao.get(orgId, dashboardId, rowId, versionId, panelId);
    checkArgument(panelOpt.isPresent(), ResourceNotFoundException::new);
    return Mappers.mapDashboardPanelToResponse(panelOpt.get());
  }

  @Override
  public GetDashboardPanelResponse updateAfterValidation(
      DashboardPanelContext panelContext, UpdateDashboardPanelRequest request) throws Exception {
    var parts = parsePanelId(panelContext.getPanelFqId().get());
    var orgId = parts[0];
    var dashboardId = parts[1];
    var rowId = parts[2];
    var panelId = parts[3];
    var versionId = panelContext.getVersionId();
    var panelOpt = panelDao.get(orgId, dashboardId, rowId, versionId, panelId);
    checkArgument(panelOpt.isPresent(), ResourceNotFoundException::new);

    var panel = panelOpt.get();
    var updated = false;
    if (request.getTitle() != null) {
      panel.setTitle(request.getTitle());
      updated = true;
    }
    if (request.getNote() != null) {
      panel.setNote(request.getNote());
      updated = true;
    }
    if (request.getQueryConfig() != null) {
      panel.setQueryConfig(mapPanelQueryConfig(request.getQueryConfig()));
      updated = true;
    }
    if (updated) {
      panelDao.save(orgId, dashboardId, rowId, versionId, panel);
    }
    return Mappers.mapDashboardPanelToResponse(panel);
  }

  @Override
  public void deleteAfterValidation(DashboardPanelContext context) {
    var parts = parsePanelId(context.getPanelFqId().get());
    var orgId = parts[0];
    var dashboardId = parts[1];
    var rowId = parts[2];
    var panelId = parts[3];
    var versionId = context.getVersionId();
    panelDao.delete(orgId, dashboardId, rowId, versionId, panelId);
  }

  private static String[] parsePanelId(String id) {
    var parts = id.split(":", 4);
    if (parts.length != 4)
      throw new IllegalArgumentException("id must be orgId:dashboardId:rowId:panelId");
    return parts;
  }
}
