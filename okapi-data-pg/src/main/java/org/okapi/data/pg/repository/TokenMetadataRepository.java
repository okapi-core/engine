/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.model.TokenStatus;
import org.okapi.data.pg.entity.TokenMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenMetadataRepository
    extends JpaRepository<TokenMetadataEntity, TokenMetadataEntity.Id> {
  List<TokenMetadataEntity> findAllByIdOrgIdAndStatusOrderByIdTokenId(
      String orgId, TokenStatus status);
}
