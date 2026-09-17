/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.DashboardVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardVariableRepository
    extends JpaRepository<DashboardVariableEntity, DashboardVariableEntity.Id> {
  List<DashboardVariableEntity> findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdOrderByIdVarName(
      String orgId, String dashboardId, String versionId);
}
