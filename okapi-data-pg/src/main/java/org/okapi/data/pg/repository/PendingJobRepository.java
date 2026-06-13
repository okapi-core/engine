/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.model.JobStatus;
import org.okapi.data.pg.entity.PendingJobEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingJobRepository extends JpaRepository<PendingJobEntity, PendingJobEntity.Id> {
  List<PendingJobEntity> findAllByIdOrgIdAndStatusOrderByIdJobId(String orgId, JobStatus status);

  List<PendingJobEntity> findAllByIdOrgIdAndSourceIdAndStatusOrderByIdJobId(
      String orgId, String sourceId, JobStatus status, Pageable pageable);
}
