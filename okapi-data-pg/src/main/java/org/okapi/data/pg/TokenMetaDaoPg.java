/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.TokenStatus;
import org.okapi.data.pg.entity.TokenMetadataEntity;
import org.okapi.data.pg.repository.TokenMetadataRepository;

public final class TokenMetaDaoPg implements TokenMetaDao {
  private final TokenMetadataRepository repository;

  public TokenMetaDaoPg(TokenMetadataRepository repository) {
    this.repository = repository;
  }

  public void createTokenMetadata(TokenMetadata token) {
    repository.saveAndFlush(
        new TokenMetadataEntity(
            id(token.getOrgId(), token.getTokenId()),
            token.getCreatorId(),
            token.getCreatedAt(),
            token.getTokenStatus()));
  }

  public TokenMetadata getTokenMetadata(String org, String token) {
    return repository.findById(id(org, token)).map(this::toDto).orElse(null);
  }

  public void updateTokenStatus(String org, String token, TokenStatus status) {
    var metadata = getTokenMetadata(org, token);
    if (metadata == null) return;
    metadata.setTokenStatus(status);
    createTokenMetadata(metadata);
  }

  public List<TokenMetadata> listTokensByOrgAndStatus(String org, TokenStatus status) {
    return repository.findAllByIdOrgIdAndStatusOrderByIdTokenId(org, status).stream()
        .map(this::toDto)
        .toList();
  }

  private TokenMetadataEntity.Id id(String orgId, String tokenId) {
    return new TokenMetadataEntity.Id(orgId, tokenId);
  }

  private TokenMetadata toDto(TokenMetadataEntity entity) {
    return TokenMetadata.builder()
        .orgId(entity.getId().getOrgId())
        .tokenId(entity.getId().getTokenId())
        .creatorId(entity.getCreatorId())
        .createdAt(entity.getCreatedAt())
        .tokenStatus(entity.getStatus())
        .build();
  }
}
