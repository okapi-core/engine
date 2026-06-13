/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.DashboardVersion;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DashboardVersionDaoPg implements DashboardVersionDao {
  private final JdbcTemplate jdbc;

  public DashboardVersionDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(DashboardVersion version) {
    jdbc.update(
        """
        INSERT INTO dashboard_versions
          (org_id, dashboard_id, version_id, status, created_at, created_by, spec_hash, note)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (org_id, dashboard_id, version_id) DO UPDATE SET
          status = EXCLUDED.status,
          created_at = EXCLUDED.created_at,
          created_by = EXCLUDED.created_by,
          spec_hash = EXCLUDED.spec_hash,
          note = EXCLUDED.note
        """,
        version.getOrgId(),
        version.getDashboardId(),
        version.getVersionId(),
        version.getStatus(),
        version.getCreatedAt(),
        version.getCreatedBy(),
        version.getSpecHash(),
        version.getNote());
  }

  public Optional<DashboardVersion> get(String org, String dashboard, String version) {
    return jdbc
        .query(
            "SELECT * FROM dashboard_versions WHERE org_id = ? AND dashboard_id = ? AND version_id = ?",
            this::map,
            org,
            dashboard,
            version)
        .stream()
        .findFirst();
  }

  public List<DashboardVersion> list(String org, String dashboard) {
    return jdbc.query(
        "SELECT * FROM dashboard_versions WHERE org_id = ? AND dashboard_id = ? ORDER BY version_id",
        this::map,
        org,
        dashboard);
  }

  private DashboardVersion map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var createdAt = rs.getLong("created_at");
    return DashboardVersion.builder()
        .orgId(rs.getString("org_id"))
        .dashboardId(rs.getString("dashboard_id"))
        .versionId(rs.getString("version_id"))
        .status(rs.getString("status"))
        .createdAt(rs.wasNull() ? null : createdAt)
        .createdBy(rs.getString("created_by"))
        .specHash(rs.getString("spec_hash"))
        .note(rs.getString("note"))
        .build();
  }
}
