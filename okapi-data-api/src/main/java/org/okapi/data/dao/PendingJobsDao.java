/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.exceptions.IllegalJobStateTransition;
import org.okapi.data.exceptions.TooManyRetriesException;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;

public interface PendingJobsDao {
  Optional<PendingJob> getPendingJob(String orgId, String jobId);

  List<PendingJob> getPendingJobsByTenantAndStatus(String orgId, JobStatus status);

  void createPendingJob(PendingJob job);

  void updatePendingJob(PendingJob job) throws IllegalJobStateTransition;

  void deletePendingJob(String orgId, String jobId);

  void retryJob(String orgId, String jobId)
      throws TooManyRetriesException, IllegalJobStateTransition;

  PendingJob updateJobStatus(String orgId, String jobId, JobStatus status)
      throws IllegalJobStateTransition;

  List<PendingJob> getJobsBySourceAndStatus(
      String orgId, String source, JobStatus status, int limit);

  PendingJob updateJobResult(String orgId, String jobId, String resultData)
      throws IllegalJobStateTransition;

  PendingJob updateJobError(String orgId, String jobId, String errorData)
      throws IllegalJobStateTransition;

  Optional<QueryResult> getRawResult(String orgId, String jobId);
}
