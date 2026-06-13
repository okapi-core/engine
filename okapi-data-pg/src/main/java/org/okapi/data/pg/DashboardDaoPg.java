/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.Dashboard;

public final class DashboardDaoPg implements DashboardDao {
  private final JdbcRecordStore store;

  public DashboardDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public Optional<Dashboard> get(String orgId, String id) {
    return store.get("dashboard", key(orgId, id), Dashboard.class);
  }

  public Dashboard save(Dashboard dashboard) {
    if (dashboard == null) throw new NullPointerException("dashboard");
    store.put(
        "dashboard",
        key(dashboard.getOrgId(), dashboard.getDashboardId()),
        dashboard.getOrgId(),
        null,
        null,
        null,
        dashboard);
    return dashboard;
  }

  public void delete(String id) throws ResourceNotFoundException {
    var dashboard =
        store.list("dashboard", null, null, Dashboard.class).stream()
            .filter(value -> id.equals(value.getDashboardId()))
            .findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException("Dashboard with id " + id + " not found"));
    store.delete("dashboard", key(dashboard.getOrgId(), id));
  }

  public List<Dashboard> getAll(String orgId) {
    return store.list("dashboard", orgId, null, Dashboard.class);
  }
}
