/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.model.DashboardPanel;

public final class DashboardPanelDaoPg implements DashboardPanelDao {
  private final JdbcRecordStore store;

  public DashboardPanelDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private String scope(String org, String dashboard, String row, String version) {
    return key(org, dashboard, version, row);
  }

  public Optional<DashboardPanel> get(
      String org, String dashboard, String row, String version, String panel) {
    return store.get(
        "dashboard-panel", key(scope(org, dashboard, row, version), panel), DashboardPanel.class);
  }

  public void save(String org, String dashboard, String row, String version, DashboardPanel panel) {
    var scope = scope(org, dashboard, row, version);
    store.put("dashboard-panel", key(scope, panel.getPanelId()), scope, null, null, null, panel);
  }

  public void delete(String org, String dashboard, String row, String version, String panel) {
    store.delete("dashboard-panel", key(scope(org, dashboard, row, version), panel));
  }

  public List<DashboardPanel> getAll(String org, String dashboard, String row, String version) {
    return store.list(
        "dashboard-panel", scope(org, dashboard, row, version), null, DashboardPanel.class);
  }
}
