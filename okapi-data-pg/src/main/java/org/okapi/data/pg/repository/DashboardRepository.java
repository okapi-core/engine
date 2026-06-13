/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import java.util.Optional;
import org.okapi.data.pg.entity.DashboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<DashboardEntity, DashboardEntity.Id> {
  List<DashboardEntity> findAllByIdOrgIdOrderByIdDashboardId(String orgId);

  Optional<DashboardEntity> findFirstByIdDashboardId(String dashboardId);
}
