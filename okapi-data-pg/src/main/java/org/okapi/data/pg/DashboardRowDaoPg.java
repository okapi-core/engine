/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.model.ResourceOrder;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DashboardRowDaoPg implements DashboardRowDao {
  private final JdbcTemplate jdbc;
  private final Gson gson;

  public DashboardRowDaoPg(JdbcTemplate jdbc, Gson gson) {
    this.jdbc = jdbc;
    this.gson = gson;
  }

  public Optional<DashboardRow> get(String org, String dashboard, String version, String row) {
    return jdbc
        .query(
            "SELECT * FROM dashboard_rows WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND row_id = ?",
            this::map,
            org,
            dashboard,
            version,
            row)
        .stream()
        .findFirst();
  }

  public void save(String org, String dashboard, String version, DashboardRow row) {
    jdbc.update(
        """
        INSERT INTO dashboard_rows
          (org_id, dashboard_id, version_id, row_id, note, title, panel_order)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
        ON CONFLICT (org_id, dashboard_id, version_id, row_id) DO UPDATE SET
          note = EXCLUDED.note,
          title = EXCLUDED.title,
          panel_order = EXCLUDED.panel_order
        """,
        org,
        dashboard,
        version,
        row.getRowId(),
        row.getNote(),
        row.getTitle(),
        row.getPanelOrder() == null ? null : gson.toJson(row.getPanelOrder()));
  }

  public void delete(String org, String dashboard, String version, String row) {
    jdbc.update(
        "DELETE FROM dashboard_rows WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND row_id = ?",
        org,
        dashboard,
        version,
        row);
  }

  public List<DashboardRow> getAll(String org, String dashboard, String version) {
    return jdbc.query(
        "SELECT * FROM dashboard_rows WHERE org_id = ? AND dashboard_id = ? AND version_id = ? ORDER BY row_id",
        this::map,
        org,
        dashboard,
        version);
  }

  private DashboardRow map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var order = rs.getString("panel_order");
    return DashboardRow.builder()
        .rowId(rs.getString("row_id"))
        .note(rs.getString("note"))
        .title(rs.getString("title"))
        .panelOrder(order == null ? null : gson.fromJson(order, ResourceOrder.class))
        .build();
  }
}
