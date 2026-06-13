/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.TokenStatus;

public interface TokenMetaDao {
  void createTokenMetadata(TokenMetadata tokenMeta);

  TokenMetadata getTokenMetadata(String orgId, String tokenId);

  void updateTokenStatus(String orgId, String tokenId, TokenStatus status);

  List<TokenMetadata> listTokensByOrgAndStatus(String orgId, TokenStatus status);
}
