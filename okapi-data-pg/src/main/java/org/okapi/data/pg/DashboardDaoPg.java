/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dashboardvars.DashVars;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DashboardDaoPg implements DashboardDao {
  private final JdbcTemplate jdbc;
  private final Gson gson;

  public DashboardDaoPg(JdbcTemplate jdbc, Gson gson) {
    this.jdbc = jdbc;
    this.gson = gson;
  }

  public Optional<Dashboard> get(String orgId, String id) {
    return jdbc
        .query(
            "SELECT * FROM dashboards WHERE org_id = ? AND dashboard_id = ?", this::map, orgId, id)
        .stream()
        .findFirst();
  }

  public Dashboard save(Dashboard dashboard) {
    if (dashboard == null) throw new NullPointerException("dashboard");
    jdbc.update(
        """
        INSERT INTO dashboards (
          org_id, dashboard_id, creator, last_editor, created_at, updated_at, title,
          description, tags, row_order, active_version, dashboard_vars, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?)
        ON CONFLICT (org_id, dashboard_id) DO UPDATE SET
          creator = EXCLUDED.creator,
          last_editor = EXCLUDED.last_editor,
          created_at = EXCLUDED.created_at,
          updated_at = EXCLUDED.updated_at,
          title = EXCLUDED.title,
          description = EXCLUDED.description,
          tags = EXCLUDED.tags,
          row_order = EXCLUDED.row_order,
          active_version = EXCLUDED.active_version,
          dashboard_vars = EXCLUDED.dashboard_vars,
          version = EXCLUDED.version
        """,
        dashboard.getOrgId(),
        dashboard.getDashboardId(),
        dashboard.getCreator(),
        dashboard.getLastEditor(),
        timestamp(dashboard.getCreated()),
        timestamp(dashboard.getUpdatedTime()),
        dashboard.getTitle(),
        dashboard.getDesc(),
        json(dashboard.getTags()),
        json(dashboard.getRowOrder()),
        dashboard.getActiveVersion(),
        json(dashboard.getDashVars()),
        dashboard.getVersion());
    return dashboard;
  }

  public void delete(String id) throws ResourceNotFoundException {
    if (jdbc.update("DELETE FROM dashboards WHERE dashboard_id = ?", id) == 0) {
      throw new ResourceNotFoundException("Dashboard with id " + id + " not found");
    }
  }

  public List<Dashboard> getAll(String orgId) {
    return jdbc.query(
        "SELECT * FROM dashboards WHERE org_id = ? ORDER BY dashboard_id", this::map, orgId);
  }

  private Dashboard map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var created = rs.getTimestamp("created_at");
    var updated = rs.getTimestamp("updated_at");
    var version = rs.getLong("version");
    return Dashboard.builder()
        .orgId(rs.getString("org_id"))
        .dashboardId(rs.getString("dashboard_id"))
        .creator(rs.getString("creator"))
        .lastEditor(rs.getString("last_editor"))
        .created(created == null ? null : created.toInstant())
        .updatedTime(updated == null ? null : updated.toInstant())
        .title(rs.getString("title"))
        .desc(rs.getString("description"))
        .tags(fromJson(rs.getString("tags"), Tags.class))
        .rowOrder(fromJson(rs.getString("row_order"), ResourceOrder.class))
        .activeVersion(rs.getString("active_version"))
        .dashVars(fromJson(rs.getString("dashboard_vars"), DashVars.class))
        .version(rs.wasNull() ? null : version)
        .build();
  }

  private Object timestamp(java.time.Instant value) {
    return value == null ? null : java.sql.Timestamp.from(value);
  }

  private String json(Object value) {
    return value == null ? null : gson.toJson(value);
  }

  private <T> T fromJson(String value, Class<T> type) {
    return value == null ? null : gson.fromJson(value, type);
  }
}
