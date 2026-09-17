/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import static org.okapi.web.service.Mappers.mapPanelQueryConfig;
import static org.okapi.web.service.Mappers.toWebCfg;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.DashboardPanel;
import org.okapi.data.model.PanelQueryConfig;
import org.okapi.data.model.ResourceOrder;
import org.okapi.web.dtos.dashboards.CreateDashboardPanelRequest;
import org.okapi.web.dtos.dashboards.GetDashboardPanelResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardPanelRequest;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.context.DashboardPanelRequestContext;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardPanelService {
  private final DashboardPanelValidator validator;
  private final DashboardPanelDao panelDao;
  private final DashboardRowDao dashboardRowDao;

  public GetDashboardPanelResponse create(
      DashboardRowRequestContext context, CreateDashboardPanelRequest request) {
    validator.validate(context);
    var panelId =
        request.getPanelId() != null ? request.getPanelId() : UUID.randomUUID().toString();
    var panel =
        DashboardPanel.builder()
            .panelId(panelId)
            .title(request.getTitle())
            .note(request.getNote())
            .build();
    if (request.getQueryConfig() != null) {
      panel.setQueryConfig(mapPanelQueryConfig(request.getGrammar(), request.getQueryConfig()));
    }
    panelDao.save(
        context.orgId(), context.dashboardId(), context.rowId(), context.versionId(), panel);

    var row =
        dashboardRowDao
            .get(context.orgId(), context.dashboardId(), context.versionId(), context.rowId())
            .orElseThrow(ResourceNotFoundException::new);
    var order = row.getPanelOrder() == null ? new ResourceOrder() : row.getPanelOrder();
    order.add(panelId);
    row.setPanelOrder(order);
    dashboardRowDao.save(context.orgId(), context.dashboardId(), context.versionId(), row);
    return Mappers.mapDashboardPanelToResponse(panel);
  }

  public GetDashboardPanelResponse read(DashboardPanelRequestContext context) {
    validator.validate(context);
    var panel =
        panelDao
            .get(
                context.orgId(),
                context.dashboardId(),
                context.rowId(),
                context.versionId(),
                context.panelId())
            .orElseThrow(ResourceNotFoundException::new);
    return Mappers.mapDashboardPanelToResponse(panel);
  }

  public GetDashboardPanelResponse update(
      DashboardPanelRequestContext context, UpdateDashboardPanelRequest request) {
    validator.validate(context);
    var panel =
        panelDao
            .get(
                context.orgId(),
                context.dashboardId(),
                context.rowId(),
                context.versionId(),
                context.panelId())
            .orElseThrow(ResourceNotFoundException::new);
    var updated = false;
    if (request.getTitle() != null) {
      panel.setTitle(request.getTitle());
      updated = true;
    }
    if (request.getNote() != null) {
      panel.setNote(request.getNote());
      updated = true;
    }
    if (request.getQueryConfig() != null || request.getGrammar() != null) {
      panel.setQueryConfig(updateQueryConfig(panel.getQueryConfig(), request));
      updated = true;
    }
    if (updated) {
      panelDao.save(
          context.orgId(), context.dashboardId(), context.rowId(), context.versionId(), panel);
    }
    return Mappers.mapDashboardPanelToResponse(panel);
  }

  public void delete(DashboardPanelRequestContext context) {
    validator.validate(context);
    panelDao.delete(
        context.orgId(),
        context.dashboardId(),
        context.rowId(),
        context.versionId(),
        context.panelId());
  }

  private PanelQueryConfig updateQueryConfig(
      PanelQueryConfig current, UpdateDashboardPanelRequest request) {
    var grammar = request.getGrammar();
    if (grammar == null && current != null) {
      grammar = current.getGrammar();
    }
    var queryConfig = request.getQueryConfig();
    if (queryConfig == null && current != null) {
      queryConfig = toWebCfg(current);
    }
    return mapPanelQueryConfig(grammar, queryConfig);
  }
}
