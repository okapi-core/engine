/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.Dashboard;

public interface DashboardDao {
  Optional<Dashboard> get(String orgId, String id);

  Dashboard save(Dashboard dto);

  void delete(String id) throws ResourceNotFoundException;

  List<Dashboard> getAll(String orgId);
}
