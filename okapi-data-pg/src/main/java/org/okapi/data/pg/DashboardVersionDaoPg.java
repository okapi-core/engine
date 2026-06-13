/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.DashboardVersion;

public final class DashboardVersionDaoPg implements DashboardVersionDao {
  private final JdbcRecordStore store;

  public DashboardVersionDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public void save(DashboardVersion version) {
    store.put(
        "dashboard-version",
        key(version.getOrgId(), version.getDashboardId(), version.getVersionId()),
        version.getOrgId(),
        version.getDashboardId(),
        version.getStatus(),
        null,
        version);
  }

  public Optional<DashboardVersion> get(String org, String dashboard, String version) {
    return store.get("dashboard-version", key(org, dashboard, version), DashboardVersion.class);
  }

  public List<DashboardVersion> list(String org, String dashboard) {
    return store.list("dashboard-version", org, dashboard, DashboardVersion.class);
  }
}
