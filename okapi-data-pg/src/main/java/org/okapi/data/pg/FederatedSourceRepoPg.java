/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.FederatedSourceRepo;
import org.okapi.data.model.FederatedSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class FederatedSourceRepoPg implements FederatedSourceRepo {
  private final JdbcTemplate jdbc;

  public FederatedSourceRepoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<FederatedSource> getSource(String tenant, String source) {
    return jdbc
        .query(
            "SELECT * FROM federated_sources WHERE org_id = ? AND source_name = ?",
            this::map,
            tenant,
            source)
        .stream()
        .findFirst();
  }

  public List<FederatedSource> getAllSources(String tenant) {
    return jdbc.query(
        "SELECT * FROM federated_sources WHERE org_id = ? ORDER BY source_name", this::map, tenant);
  }

  public void createSource(FederatedSource source) {
    jdbc.update(
        """
        INSERT INTO federated_sources
          (org_id, source_name, source_type, registration_token, created_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (org_id, source_name) DO UPDATE SET
          source_type = EXCLUDED.source_type,
          registration_token = EXCLUDED.registration_token,
          created_at = EXCLUDED.created_at
        """,
        source.getOrgId(),
        source.getSourceName(),
        source.getSourceType(),
        source.getRegistrationToken(),
        source.getCreated() == null ? null : java.sql.Timestamp.from(source.getCreated()));
  }

  public void deleteSource(String tenant, String source) {
    jdbc.update(
        "DELETE FROM federated_sources WHERE org_id = ? AND source_name = ?", tenant, source);
  }

  private FederatedSource map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var created = rs.getTimestamp("created_at");
    return FederatedSource.builder()
        .orgId(rs.getString("org_id"))
        .sourceName(rs.getString("source_name"))
        .sourceType(rs.getString("source_type"))
        .registrationToken(rs.getString("registration_token"))
        .created(created == null ? null : created.toInstant())
        .build();
  }
}
