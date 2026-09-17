/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.rows;

import static org.okapi.validation.OkapiChecks.checkArgument;
import static org.okapi.web.service.Mappers.toRowResponse;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.Dashboard;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.model.ResourceOrder;
import org.okapi.web.dtos.dashboards.CreateDashboardRowRequest;
import org.okapi.web.dtos.dashboards.GetDashboardRowResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardRowRequest;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardRowService {
  private final DashboardRowValidator validator;
  private final DashboardRowDao rowDao;
  private final DashboardPanelDao panelDao;
  private final DashboardDao dashboardDao;

  public GetDashboardRowResponse create(
      DashboardVersionRequestContext context, CreateDashboardRowRequest request) {
    validator.validate(context);
    var dashboard = getDashboardOrThrow(context.orgId(), context.dashboardId());
    var rowId = request.getRowId() != null ? request.getRowId() : UUID.randomUUID().toString();
    var row =
        DashboardRow.builder()
            .rowId(rowId)
            .title(request.getTitle())
            .note(request.getDescription())
            .build();
    rowDao.save(context.orgId(), context.dashboardId(), context.versionId(), row);

    var currentOrder =
        dashboard.getRowOrder() == null ? new ResourceOrder() : dashboard.getRowOrder();
    currentOrder.add(rowId);
    dashboard.setRowOrder(currentOrder);
    dashboardDao.save(dashboard);
    return toRowResponse(row, List.of());
  }

  public GetDashboardRowResponse read(DashboardRowRequestContext context) {
    validator.validate(context);
    var row =
        rowDao
            .get(context.orgId(), context.dashboardId(), context.versionId(), context.rowId())
            .orElseThrow(ResourceNotFoundException::new);
    var panels =
        panelDao.getAll(
            context.orgId(), context.dashboardId(), context.rowId(), context.versionId());
    return toRowResponse(row, panels);
  }

  public GetDashboardRowResponse update(
      DashboardRowRequestContext context, UpdateDashboardRowRequest request) {
    validator.validate(context);
    var row =
        rowDao
            .get(context.orgId(), context.dashboardId(), context.versionId(), context.rowId())
            .orElseThrow(ResourceNotFoundException::new);
    var updated = false;
    if (request.getTitle() != null) {
      row.setTitle(request.getTitle());
      updated = true;
    }
    if (request.getDescription() != null) {
      row.setNote(request.getDescription());
      updated = true;
    }
    if (request.getPanelIds() != null) {
      row.setPanelOrder(new ResourceOrder(request.getPanelIds()));
      updated = true;
    }
    if (updated) {
      rowDao.save(context.orgId(), context.dashboardId(), context.versionId(), row);
    }
    var panels =
        panelDao.getAll(
            context.orgId(), context.dashboardId(), context.rowId(), context.versionId());
    return toRowResponse(row, panels);
  }

  public void delete(DashboardRowRequestContext context) {
    validator.validate(context);
    rowDao.delete(context.orgId(), context.dashboardId(), context.versionId(), context.rowId());
  }

  private Dashboard getDashboardOrThrow(String orgId, String dashboardId) {
    var dashboard = dashboardDao.get(orgId, dashboardId);
    checkArgument(dashboard.isPresent(), ResourceNotFoundException::new);
    return dashboard.get();
  }
}
