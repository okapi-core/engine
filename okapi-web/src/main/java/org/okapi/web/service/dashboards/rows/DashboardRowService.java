/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.rows;

import static org.okapi.validation.OkapiChecks.checkArgument;
import static org.okapi.web.service.Mappers.toRowResponse;

import java.util.List;
import java.util.UUID;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.dtos.dashboards.*;
import org.okapi.web.service.AbstractValidatedCrudService;
import org.okapi.web.service.ProtectedResourceContext;
import org.okapi.web.service.context.DashboardRowAccessContext;
import org.springframework.stereotype.Service;

@Service
public class DashboardRowService
    extends AbstractValidatedCrudService<
        ProtectedResourceContext,
        DashboardRowAccessContext,
        CreateDashboardRowRequest,
        UpdateDashboardRowRequest,
        GetDashboardRowResponse> {

  private final DashboardRowDao rowDao;
  private final DashboardPanelDao panelDao;
  private final DashboardDao dashboardDao;

  public DashboardRowService(
      DashboardRowValidator validator,
      DashboardRowDao rowDao,
      DashboardPanelDao panelDao,
      DashboardDao dashboardDao) {
    super(validator);
    this.rowDao = rowDao;
    this.panelDao = panelDao;
    this.dashboardDao = dashboardDao;
  }

  @Override
  public GetDashboardRowResponse createAfterValidation(
      DashboardRowAccessContext ctx, CreateDashboardRowRequest request) {
    var rowId = request.getRowId() != null ? request.getRowId() : UUID.randomUUID().toString();
    var versionId = ctx.getVersionId();
    var row =
        DashboardRow.builder()
            .rowId(rowId)
            .title(request.getTitle())
            .note(request.getDescription())
            .build();
    rowDao.save(request.getOrgId(), request.getDashboardId(), versionId, row);

    // update drawing order in dashboard
    var dash = ctx.getDashboardDdb();
    var currentOrder = (dash.getRowOrder() == null) ? new ResourceOrder() : dash.getRowOrder();
    currentOrder.add(rowId);
    dash.setRowOrder(currentOrder);
    dashboardDao.save(dash);
    return toRowResponse(row, List.of());
  }

  @Override
  public GetDashboardRowResponse readAfterValidation(DashboardRowAccessContext rowContext)
      throws ResourceNotFoundException {
    var id = rowContext.getRowId();
    var rowOpt =
        rowDao.get(id.getOrgId(), id.getDashboardId(), rowContext.getVersionId(), id.getRowId());
    checkArgument(rowOpt.isPresent(), ResourceNotFoundException::new);

    var panels =
        panelDao.getAll(
            id.getOrgId(), id.getDashboardId(), id.getRowId(), rowContext.getVersionId());
    return toRowResponse(rowOpt.get(), panels);
  }

  @Override
  public GetDashboardRowResponse updateAfterValidation(
      DashboardRowAccessContext rowContext, UpdateDashboardRowRequest request) throws Exception {
    var id = rowContext.getRowId();
    var rowOpt =
        rowDao.get(id.getOrgId(), id.getDashboardId(), rowContext.getVersionId(), id.getRowId());
    checkArgument(rowOpt.isPresent(), ResourceNotFoundException::new);

    var row = rowOpt.get();
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
      rowDao.save(id.getOrgId(), id.getDashboardId(), rowContext.getVersionId(), row);
    }

    var panels =
        panelDao.getAll(
            id.getOrgId(), id.getDashboardId(), id.getRowId(), rowContext.getVersionId());
    return toRowResponse(row, panels);
  }

  @Override
  public void deleteAfterValidation(DashboardRowAccessContext rowContext)
      throws UnAuthorizedException {
    var id = rowContext.getRowId();
    rowDao.delete(id.getOrgId(), id.getDashboardId(), rowContext.getVersionId(), id.getRowId());
  }

  private static String[] parseRowId(String id) {
    var parts = id.split(":", 3);
    if (parts.length != 3) throw new IllegalArgumentException("id must be orgId:dashboardId:rowId");
    return parts;
  }
}
