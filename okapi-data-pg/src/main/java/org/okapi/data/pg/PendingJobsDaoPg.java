/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.List;
import java.util.Optional;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.dao.ResultUploader;
import org.okapi.data.exceptions.*;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;

public final class PendingJobsDaoPg implements PendingJobsDao {
  private static final int MAX_RETRY_ATTEMPTS = 5;
  private final JdbcRecordStore store;
  private final ResultUploader uploader;

  public PendingJobsDaoPg(JdbcRecordStore store, ResultUploader uploader) {
    this.store = store;
    this.uploader = uploader;
  }

  public Optional<PendingJob> getPendingJob(String org, String job) {
    return store.get("pending-job", key(org, job), PendingJob.class);
  }

  public List<PendingJob> getPendingJobsByTenantAndStatus(String org, JobStatus status) {
    return store.listByStatus(
        "pending-job", org, null, status.name(), Integer.MAX_VALUE, PendingJob.class);
  }

  public void createPendingJob(PendingJob job) {
    save(job);
  }

  public void updatePendingJob(PendingJob job) throws IllegalJobStateTransition {
    var existing =
        getPendingJob(job.getOrgId(), job.getJobId())
            .orElseThrow(() -> new JobNotFoundException("Job not found for update"));
    if (existing.getJobStatus() != job.getJobStatus()
        && !canTransition(existing.getJobStatus(), job.getJobStatus())) {
      throw new IllegalJobStateTransition(
          "Invalid state transition from " + existing.getJobStatus() + " to " + job.getJobStatus());
    }
    save(job);
  }

  public void deletePendingJob(String org, String job) {
    store.delete("pending-job", key(org, job));
  }

  public void retryJob(String org, String job)
      throws TooManyRetriesException, IllegalJobStateTransition {
    var value =
        getPendingJob(org, job)
            .orElseThrow(() -> new JobNotFoundException("Job not found for retry"));
    if (value.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
      throw new TooManyRetriesException("Max retry attempts reached for job: " + org + "/" + job);
    }
    value.setJobStatus(JobStatus.PENDING);
    value.setSourceId(null);
    value.setAssignedAt(null);
    value.setAttemptCount(value.getAttemptCount() + 1);
    updatePendingJob(value);
  }

  public PendingJob updateJobStatus(String org, String job, JobStatus status)
      throws IllegalJobStateTransition {
    var value = getPendingJob(org, job).orElse(null);
    if (value == null) return null;
    value.setJobStatus(status);
    updatePendingJob(value);
    return value;
  }

  public List<PendingJob> getJobsBySourceAndStatus(
      String org, String source, JobStatus status, int limit) {
    return store.listByStatus("pending-job", org, source, status.name(), limit, PendingJob.class);
  }

  public PendingJob updateJobResult(String org, String job, String result)
      throws IllegalJobStateTransition {
    var value =
        getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
    value.setResultLocation(uploader.uploadResult(org, job, result));
    value.setJobStatus(JobStatus.COMPLETED);
    updatePendingJob(value);
    return value;
  }

  public PendingJob updateJobError(String org, String job, String error)
      throws IllegalJobStateTransition {
    var value =
        getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
    value.setErrorLocation(uploader.uploadResult(org, job, error));
    value.setJobStatus(JobStatus.FAILED);
    updatePendingJob(value);
    return value;
  }

  public Optional<QueryResult> getRawResult(String org, String job) {
    var value =
        getPendingJob(org, job).orElseThrow(() -> new JobNotFoundException("Job not found"));
    if (value.getJobStatus() != JobStatus.COMPLETED && value.getJobStatus() != JobStatus.FAILED)
      return Optional.empty();
    return Optional.of(store.gson().fromJson(uploader.getRawResult(org, job), QueryResult.class));
  }

  private void save(PendingJob job) {
    store.put(
        "pending-job",
        key(job.getOrgId(), job.getJobId()),
        job.getOrgId(),
        null,
        job.getJobStatus().name(),
        job.getSourceId(),
        job);
  }

  private boolean canTransition(JobStatus from, JobStatus to) {
    return switch (from) {
      case PENDING -> to == JobStatus.IN_PROGRESS || to == JobStatus.CANCELLED;
      case IN_PROGRESS ->
          to == JobStatus.COMPLETED || to == JobStatus.FAILED || to == JobStatus.CANCELLED;
      case FAILED -> to == JobStatus.PENDING;
      case CANCELLED, COMPLETED -> false;
    };
  }
}
