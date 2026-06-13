/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.data.model.JobStatus;

@Entity
@Table(name = "pending_jobs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PendingJobEntity {
  @EmbeddedId private Id id;

  @Column(name = "result_location")
  private String resultLocation;

  @Column(name = "error_location")
  private String errorLocation;

  @Enumerated(EnumType.STRING)
  private JobStatus status;

  @Column(name = "source_id")
  private String sourceId;

  @Column(name = "query_text")
  private String queryText;

  @Column(name = "query_source_id")
  private String querySourceId;

  @Column(name = "attempt_count")
  private int attemptCount;

  @Column(name = "created_at")
  private Long createdAt;

  @Column(name = "assigned_at")
  private Long assignedAt;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "job_id")
    private String jobId;
  }
}
