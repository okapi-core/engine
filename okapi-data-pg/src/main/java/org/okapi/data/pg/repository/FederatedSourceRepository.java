/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.FederatedSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FederatedSourceRepository
    extends JpaRepository<FederatedSourceEntity, FederatedSourceEntity.Id> {
  List<FederatedSourceEntity> findAllByIdOrgIdOrderByIdSourceName(String orgId);
}
