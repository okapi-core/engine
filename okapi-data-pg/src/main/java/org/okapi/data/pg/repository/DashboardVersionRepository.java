/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.DashboardVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardVersionRepository
    extends JpaRepository<DashboardVersionEntity, DashboardVersionEntity.Id> {
  List<DashboardVersionEntity> findAllByIdOrgIdAndIdDashboardIdOrderByIdVersionId(
      String orgId, String dashboardId);
}
