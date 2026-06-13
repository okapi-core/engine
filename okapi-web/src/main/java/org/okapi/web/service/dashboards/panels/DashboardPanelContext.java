/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.data.model.Dashboard;
import org.okapi.exceptions.BadRequestException;
import org.okapi.web.service.context.OrgMemberContext;

@AllArgsConstructor
public class DashboardPanelContext {
  @Getter OrgMemberContext orgMemberContext;
  DashboardPanelId panelFqId;
  @Getter Dashboard dashboardDdb;
  @Getter String versionId;

  public Optional<String> getPanelFqId() {
    if (panelFqId == null) {
      return Optional.empty();
    }
    return Optional.of(panelFqId.getFqId());
  }

  public static final DashboardPanelContext of(
      OrgMemberContext memberContext, Dashboard dashboardDdb, String versionId) {
    return new DashboardPanelContext(memberContext, null, dashboardDdb, versionId);
  }

  public static DashboardPanelContext of(
      OrgMemberContext memberContext, String panelFqId, Dashboard dashboardDdb, String versionId)
      throws BadRequestException {
    var parsedId = DashboardPanelIdParser.parseStatic(panelFqId);
    return new DashboardPanelContext(memberContext, parsedId, dashboardDdb, versionId);
  }
}
