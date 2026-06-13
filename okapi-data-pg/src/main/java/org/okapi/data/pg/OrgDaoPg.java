/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.Optional;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.model.Organization;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OrgDaoPg implements OrgDao {
  private final JdbcTemplate jdbc;

  public OrgDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Organization> findById(String id) {
    return jdbc
        .query(
            "SELECT * FROM organizations WHERE org_id = ?",
            (rs, row) ->
                Organization.builder()
                    .orgId(rs.getString("org_id"))
                    .orgName(rs.getString("org_name"))
                    .orgCreator(rs.getString("org_creator"))
                    .created(
                        rs.getTimestamp("created_at") == null
                            ? null
                            : rs.getTimestamp("created_at").toInstant())
                    .build(),
            id)
        .stream()
        .findFirst();
  }

  public void save(Organization organization) {
    jdbc.update(
        """
        INSERT INTO organizations (org_id, org_name, org_creator, created_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (org_id) DO UPDATE SET
          org_name = EXCLUDED.org_name,
          org_creator = EXCLUDED.org_creator,
          created_at = EXCLUDED.created_at
        """,
        organization.getOrgId(),
        organization.getOrgName(),
        organization.getOrgCreator(),
        organization.getCreated() == null
            ? null
            : java.sql.Timestamp.from(organization.getCreated()));
  }
}
