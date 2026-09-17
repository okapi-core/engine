/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.DashboardVariable;

public interface DashboardVarDao {
  Optional<DashboardVariable> get(String orgId, String dashboardId, String versionId, String name);

  DashboardVariable save(
      String orgId, String dashboardId, String versionId, DashboardVariable variable);

  void delete(String orgId, String dashboardId, String versionId, String name)
      throws ResourceNotFoundException;

  List<DashboardVariable> list(String orgId, String dashboardId, String versionId);
}
