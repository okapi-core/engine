/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.model.DashboardVariable;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DashboardVarDaoPg implements DashboardVarDao {
  private final JdbcTemplate jdbc;

  public DashboardVarDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<DashboardVariable> get(
      String org, String dashboard, String version, String name) {
    return jdbc
        .query(
            "SELECT * FROM dashboard_variables WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND var_name = ?",
            this::map,
            org,
            dashboard,
            version,
            name)
        .stream()
        .findFirst();
  }

  public DashboardVariable save(
      String org, String dashboard, String version, DashboardVariable variable) {
    if (variable == null) throw new NullPointerException("variable");
    jdbc.update(
        """
        INSERT INTO dashboard_variables
          (org_id, dashboard_id, version_id, var_name, tag, var_type)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (org_id, dashboard_id, version_id, var_name) DO UPDATE SET
          tag = EXCLUDED.tag,
          var_type = EXCLUDED.var_type
        """,
        org,
        dashboard,
        version,
        variable.getVarName(),
        variable.getTag(),
        variable.getVarType().name());
    return variable;
  }

  public void delete(String org, String dashboard, String version, String name) {
    jdbc.update(
        "DELETE FROM dashboard_variables WHERE org_id = ? AND dashboard_id = ? AND version_id = ? AND var_name = ?",
        org,
        dashboard,
        version,
        name);
  }

  public List<DashboardVariable> list(String org, String dashboard, String version) {
    return jdbc.query(
        "SELECT * FROM dashboard_variables WHERE org_id = ? AND dashboard_id = ? AND version_id = ? ORDER BY var_name",
        this::map,
        org,
        dashboard,
        version);
  }

  private DashboardVariable map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return DashboardVariable.builder()
        .varName(rs.getString("var_name"))
        .tag(rs.getString("tag"))
        .varType(DashboardVariable.Type.valueOf(rs.getString("var_type")))
        .build();
  }
}
