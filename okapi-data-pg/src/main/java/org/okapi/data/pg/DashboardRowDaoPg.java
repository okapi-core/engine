/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.DashboardRow;

public final class DashboardRowDaoPg implements DashboardRowDao {
  private final JdbcRecordStore store;

  public DashboardRowDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private String scope(String org, String dashboard, String version) {
    return key(org, dashboard, version);
  }

  public Optional<DashboardRow> get(String org, String dashboard, String version, String row) {
    return store.get("dashboard-row", key(scope(org, dashboard, version), row), DashboardRow.class);
  }

  public void save(String org, String dashboard, String version, DashboardRow row) {
    var scope = scope(org, dashboard, version);
    store.put("dashboard-row", key(scope, row.getRowId()), scope, null, null, null, row);
  }

  public void delete(String org, String dashboard, String version, String row) {
    store.delete("dashboard-row", key(scope(org, dashboard, version), row));
  }

  public List<DashboardRow> getAll(String org, String dashboard, String version) {
    return store.list("dashboard-row", scope(org, dashboard, version), null, DashboardRow.class);
  }
}
