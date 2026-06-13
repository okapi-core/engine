/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards.rows;

import lombok.Getter;
import org.okapi.exceptions.BadRequestException;
import org.okapi.web.service.ResourceIdCreator;

@Getter
public class DashboardRowId {
  String fqId;
  String orgId;
  String dashboardId;
  String rowId;

  public DashboardRowId(String fqId) {
    this.fqId = fqId;
    // Assuming the fqId is in the format "orgId:dashboardId:rowId"
    String[] parts = fqId.split(":");
    if (parts.length == 3) {
      this.orgId = parts[0];
      this.dashboardId = parts[1];
      this.rowId = parts[2];
    } else {
      throw new BadRequestException("Invalid fqId format");
    }
  }

  public String getOrgDashKey(String versionId) {
    return ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
  }
}
