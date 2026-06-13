/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.DashboardRowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRowRepository
    extends JpaRepository<DashboardRowEntity, DashboardRowEntity.Id> {
  List<DashboardRowEntity> findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdOrderByIdRowId(
      String orgId, String dashboardId, String versionId);
}
