/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.DashboardPanelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardPanelRepository
    extends JpaRepository<DashboardPanelEntity, DashboardPanelEntity.Id> {
  List<DashboardPanelEntity>
      findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdAndIdRowIdOrderByIdPanelId(
          String orgId, String dashboardId, String versionId, String rowId);
}
