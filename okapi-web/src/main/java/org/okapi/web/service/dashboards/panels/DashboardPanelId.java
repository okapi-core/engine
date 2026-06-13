/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.panels;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.okapi.exceptions.BadRequestException;
import org.okapi.web.service.ResourceIdCreator;

@AllArgsConstructor
@Getter
public class DashboardPanelId {
  String fqId;
  String orgId;
  String dashboardId;
  String rowId;
  String panelId;

  public DashboardPanelId(String fqId) {
    this.fqId = fqId;
    // Assuming the fqId is in the format "orgId:dashboardId:rowId:panelId"
    String[] parts = fqId.split(":");
    if (parts.length == 4) {
      this.orgId = parts[0];
      this.dashboardId = parts[1];
      this.rowId = parts[2];
      this.panelId = parts[3];
    } else {
      throw new BadRequestException("Invalid fqId format");
    }
  }

  public String getOrgDashKey(String versionId) {
    return ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
  }

  public String getOrgDashRowKey(String versionId) {
    return ResourceIdCreator.createResourceId(orgId, dashboardId, versionId, rowId);
  }
}
