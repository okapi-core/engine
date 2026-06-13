/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.TokenStatus;

public final class TokenMetaDaoPg implements TokenMetaDao {
  private final JdbcRecordStore store;

  public TokenMetaDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public void createTokenMetadata(TokenMetadata token) {
    store.put(
        "token",
        key(token.getOrgId(), token.getTokenId()),
        token.getOrgId(),
        null,
        token.getTokenStatus().name(),
        null,
        token);
  }

  public TokenMetadata getTokenMetadata(String org, String token) {
    return store.get("token", key(org, token), TokenMetadata.class).orElse(null);
  }

  public void updateTokenStatus(String org, String token, TokenStatus status) {
    var metadata = getTokenMetadata(org, token);
    if (metadata == null) return;
    metadata.setTokenStatus(status);
    createTokenMetadata(metadata);
  }

  public List<TokenMetadata> listTokensByOrgAndStatus(String org, TokenStatus status) {
    return store.listByStatus(
        "token", org, null, status.name(), Integer.MAX_VALUE, TokenMetadata.class);
  }
}
