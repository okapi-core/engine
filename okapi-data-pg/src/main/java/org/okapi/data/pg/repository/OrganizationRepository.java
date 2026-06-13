/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.time.Instant;
import org.okapi.data.pg.entity.OrganizationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO organizations (org_id, org_name, org_creator, created_at)
          VALUES (:orgId, :orgName, :orgCreator, :created)
          ON CONFLICT (org_id) DO UPDATE SET
            org_name = EXCLUDED.org_name,
            org_creator = EXCLUDED.org_creator,
            created_at = EXCLUDED.created_at
          """,
      nativeQuery = true)
  void upsert(String orgId, String orgName, String orgCreator, Instant created);
}
