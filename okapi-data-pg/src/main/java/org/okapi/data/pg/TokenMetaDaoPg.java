/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.TokenStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TokenMetaDaoPg implements TokenMetaDao {
  private final JdbcTemplate jdbc;

  public TokenMetaDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void createTokenMetadata(TokenMetadata token) {
    jdbc.update(
        """
        INSERT INTO token_metadata (org_id, token_id, creator_id, created_at, status)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (org_id, token_id) DO UPDATE SET
          creator_id = EXCLUDED.creator_id,
          created_at = EXCLUDED.created_at,
          status = EXCLUDED.status
        """,
        token.getOrgId(),
        token.getTokenId(),
        token.getCreatorId(),
        token.getCreatedAt(),
        token.getTokenStatus().name());
  }

  public TokenMetadata getTokenMetadata(String org, String token) {
    return jdbc
        .query(
            "SELECT * FROM token_metadata WHERE org_id = ? AND token_id = ?", this::map, org, token)
        .stream()
        .findFirst()
        .orElse(null);
  }

  public void updateTokenStatus(String org, String token, TokenStatus status) {
    var metadata = getTokenMetadata(org, token);
    if (metadata == null) return;
    jdbc.update(
        "UPDATE token_metadata SET status = ? WHERE org_id = ? AND token_id = ?",
        status.name(),
        org,
        token);
  }

  public List<TokenMetadata> listTokensByOrgAndStatus(String org, TokenStatus status) {
    return jdbc.query(
        "SELECT * FROM token_metadata WHERE org_id = ? AND status = ? ORDER BY token_id",
        this::map,
        org,
        status.name());
  }

  private TokenMetadata map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var createdAt = rs.getLong("created_at");
    return TokenMetadata.builder()
        .orgId(rs.getString("org_id"))
        .tokenId(rs.getString("token_id"))
        .creatorId(rs.getString("creator_id"))
        .createdAt(rs.wasNull() ? null : createdAt)
        .tokenStatus(TokenStatus.valueOf(rs.getString("status")))
        .build();
  }
}
