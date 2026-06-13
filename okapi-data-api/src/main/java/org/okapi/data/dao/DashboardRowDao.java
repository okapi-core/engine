/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.model.DashboardRow;

public interface DashboardRowDao {
  Optional<DashboardRow> get(String orgId, String dashboardId, String versionId, String rowId);

  void save(String orgId, String dashboardId, String versionId, DashboardRow row);

  void delete(String orgId, String dashboardId, String versionId, String rowId);

  List<DashboardRow> getAll(String orgId, String dashboardId, String versionId);
}
