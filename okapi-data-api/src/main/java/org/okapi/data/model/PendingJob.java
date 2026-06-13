/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor
@Getter
@Setter
public class PendingJob {
  private String orgId;
  private String jobId;
  private String resultLocation;
  private String errorLocation;
  private JobStatus jobStatus;
  private String sourceId;
  private DataSourceQuery query;
  private int attemptCount;
  private Long createdAt;
  private Long assignedAt;
}
