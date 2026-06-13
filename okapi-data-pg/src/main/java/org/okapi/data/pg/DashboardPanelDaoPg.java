/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.model.DashboardPanel;
import org.okapi.data.model.MultiQueryPanelConfig;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DashboardPanelDaoPg implements DashboardPanelDao {
  private final JdbcTemplate jdbc;
  private final Gson gson;

  public DashboardPanelDaoPg(JdbcTemplate jdbc, Gson gson) {
    this.jdbc = jdbc;
    this.gson = gson;
  }

  public Optional<DashboardPanel> get(
      String org, String dashboard, String row, String version, String panel) {
    return jdbc
        .query(
            "SELECT * FROM dashboard_panels WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND row_id = ? AND panel_id = ?",
            this::map,
            org,
            dashboard,
            version,
            row,
            panel)
        .stream()
        .findFirst();
  }

  public void save(String org, String dashboard, String row, String version, DashboardPanel panel) {
    jdbc.update(
        """
        INSERT INTO dashboard_panels
          (org_id, dashboard_id, version_id, row_id, panel_id, note, title, query_config)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (org_id, dashboard_id, version_id, row_id, panel_id) DO UPDATE SET
          note = EXCLUDED.note,
          title = EXCLUDED.title,
          query_config = EXCLUDED.query_config
        """,
        org,
        dashboard,
        version,
        row,
        panel.getPanelId(),
        panel.getNote(),
        panel.getTitle(),
        panel.getQueryConfig() == null ? null : gson.toJson(panel.getQueryConfig()));
  }

  public void delete(String org, String dashboard, String row, String version, String panel) {
    jdbc.update(
        "DELETE FROM dashboard_panels WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND row_id = ? AND panel_id = ?",
        org,
        dashboard,
        version,
        row,
        panel);
  }

  public List<DashboardPanel> getAll(String org, String dashboard, String row, String version) {
    return jdbc.query(
        "SELECT * FROM dashboard_panels WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND row_id = ? ORDER BY panel_id",
        this::map,
        org,
        dashboard,
        version,
        row);
  }

  private DashboardPanel map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var config = rs.getString("query_config");
    return DashboardPanel.builder()
        .panelId(rs.getString("panel_id"))
        .note(rs.getString("note"))
        .title(rs.getString("title"))
        .queryConfig(config == null ? null : gson.fromJson(config, MultiQueryPanelConfig.class))
        .build();
  }
}
