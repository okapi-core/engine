/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.dao.ResultUploader;
import org.okapi.data.exceptions.*;
import org.okapi.data.model.DataSourceQuery;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PendingJobsDaoPg implements PendingJobsDao {
  private static final int MAX_RETRY_ATTEMPTS = 5;
  private final JdbcTemplate jdbc;
  private final ResultUploader uploader;
  private final Gson gson;

  public PendingJobsDaoPg(JdbcTemplate jdbc, ResultUploader uploader, Gson gson) {
    this.jdbc = jdbc;
    this.uploader = uploader;
    this.gson = gson;
  }

  public Optional<PendingJob> getPendingJob(String org, String job) {
    return jdbc
        .query("SELECT * FROM pending_jobs WHERE org_id = ? AND job_id = ?", this::map, org, job)
        .stream()
        .findFirst();
  }

  public List<PendingJob> getPendingJobsByTenantAndStatus(String org, JobStatus status) {
    return jdbc.query(
        "SELECT * FROM pending_jobs WHERE org_id = ? AND status = ? ORDER BY job_id",
        this::map,
        org,
        status.name());
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
    jdbc.update("DELETE FROM pending_jobs WHERE org_id = ? AND job_id = ?", org, job);
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
    return jdbc.query(
        """
        SELECT * FROM pending_jobs
        WHERE org_id = ? AND source_id = ? AND status = ?
        ORDER BY job_id
        LIMIT ?
        """,
        this::map,
        org,
        source,
        status.name(),
        limit);
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
    return Optional.of(gson.fromJson(uploader.getRawResult(org, job), QueryResult.class));
  }

  private void save(PendingJob job) {
    jdbc.update(
        """
        INSERT INTO pending_jobs (
          org_id, job_id, result_location, error_location, status, source_id,
          query_text, query_source_id, attempt_count, created_at, assigned_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (org_id, job_id) DO UPDATE SET
          result_location = EXCLUDED.result_location,
          error_location = EXCLUDED.error_location,
          status = EXCLUDED.status,
          source_id = EXCLUDED.source_id,
          query_text = EXCLUDED.query_text,
          query_source_id = EXCLUDED.query_source_id,
          attempt_count = EXCLUDED.attempt_count,
          created_at = EXCLUDED.created_at,
          assigned_at = EXCLUDED.assigned_at
        """,
        job.getOrgId(),
        job.getJobId(),
        job.getResultLocation(),
        job.getErrorLocation(),
        job.getJobStatus().name(),
        job.getSourceId(),
        job.getQuery() == null ? null : job.getQuery().query(),
        job.getQuery() == null ? null : job.getQuery().sourceId(),
        job.getAttemptCount(),
        job.getCreatedAt(),
        job.getAssignedAt());
  }

  private PendingJob map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var queryText = rs.getString("query_text");
    var querySource = rs.getString("query_source_id");
    var createdAt = rs.getLong("created_at");
    Long created = rs.wasNull() ? null : createdAt;
    var assignedAt = rs.getLong("assigned_at");
    Long assigned = rs.wasNull() ? null : assignedAt;
    return PendingJob.builder()
        .orgId(rs.getString("org_id"))
        .jobId(rs.getString("job_id"))
        .resultLocation(rs.getString("result_location"))
        .errorLocation(rs.getString("error_location"))
        .jobStatus(JobStatus.valueOf(rs.getString("status")))
        .sourceId(rs.getString("source_id"))
        .query(
            queryText == null && querySource == null
                ? null
                : new DataSourceQuery(queryText, querySource))
        .attemptCount(rs.getInt("attempt_count"))
        .createdAt(created)
        .assignedAt(assigned)
        .build();
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
