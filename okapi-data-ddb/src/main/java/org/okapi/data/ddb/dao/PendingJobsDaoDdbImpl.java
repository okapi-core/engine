/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import static org.okapi.data.dto.TablesAndIndexes.PENDING_JOBS_TABLE;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.dao.ResultUploader;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.dto.*;
import org.okapi.data.exceptions.IllegalJobStateTransition;
import org.okapi.data.exceptions.JobNotFoundException;
import org.okapi.data.exceptions.TooManyRetriesException;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class PendingJobsDaoDdbImpl implements PendingJobsDao {

  public static final Integer MAX_RETRY_ATTEMPTS = 5;
  private final DynamoDbTable<PendingJobDdb> table;
  ResultUploader resultUploader;
  Gson gson = new Gson();

  @Inject
  public PendingJobsDaoDdbImpl(DynamoDbEnhancedClient enhancedClient, ResultUploader uploader) {
    this.table =
        enhancedClient.table(PENDING_JOBS_TABLE, TableSchema.fromBean(PendingJobDdb.class));
    this.resultUploader = uploader;
  }

  @Override
  public Optional<PendingJob> getPendingJob(String orgId, String jobId) {
    // No direct index for jobId, scan and filter the first match
    var item = table.getItem(Key.builder().partitionValue(orgId).sortValue(jobId).build());
    return Optional.ofNullable(DdbMapper.toApi(item));
  }

  @Override
  public List<PendingJob> getPendingJobsByTenantAndStatus(String orgId, JobStatus status) {
    var query =
        table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(orgId).build()))
                .build());
    var jobs = Lists.newArrayList(new FlatteningIterator<>(query.iterator()));
    return jobs.stream()
        .filter(j -> status.name().equals(j.getJobStatus().name()))
        .map(DdbMapper::toApi)
        .toList();
  }

  @Override
  public void createPendingJob(PendingJob job) {
    table.putItem(DdbMapper.toDdb(job));
  }

  @Override
  public void updatePendingJob(PendingJob job) throws IllegalJobStateTransition {
    var existing = getPendingJob(job.getOrgId(), job.getJobId());
    if (existing.isEmpty()) {
      throw new JobNotFoundException(
          "Job not found for update: " + job.getOrgId() + "/" + job.getJobId());
    }
    checkStateTransition(job, existing.get());
    var expr =
        Expression.builder()
            .expression("#st = :from")
            .expressionNames(java.util.Map.of("#st", TableAttributes.JOB_STATUS))
            .expressionValues(
                java.util.Map.of(
                    ":from",
                    AttributeValue.builder().s(existing.get().getJobStatus().name()).build()))
            .build();

    table.updateItem(
        UpdateItemEnhancedRequest.builder(PendingJobDdb.class)
            .item(DdbMapper.toDdb(job))
            .conditionExpression(expr)
            .build());
  }

  protected void checkStateTransition(PendingJob job, PendingJob existingJob)
      throws IllegalJobStateTransition {
    var from = existingJob.getJobStatus();
    var to = job.getJobStatus();
    if (!from.equals(to)
        && !PendingJobsStateMachine.canTransition(
            JOB_STATUS.valueOf(from.name()), JOB_STATUS.valueOf(to.name()))) {
      throw new IllegalJobStateTransition(
          "Invalid state transition from " + from + " to " + to + " for job " + job.getJobId());
    }
  }

  @Override
  public void deletePendingJob(String orgId, String jobId) {
    var maybe = getPendingJob(orgId, jobId);
    maybe.ifPresent(
        job ->
            table.deleteItem(
                Key.builder().partitionValue(job.getOrgId()).sortValue(job.getJobId()).build()));
  }

  @Override
  public void retryJob(String orgId, String jobId)
      throws TooManyRetriesException, IllegalJobStateTransition {
    Optional<PendingJob> optionalPendingJobDto = getPendingJob(orgId, jobId);
    if (optionalPendingJobDto.isEmpty())
      throw new JobNotFoundException("Job not found for retry: " + orgId + "/" + jobId);
    // Update fields using DTO setters
    var item = optionalPendingJobDto.get();
    if (item.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
      throw new TooManyRetriesException(
          "Max retry attempts reached for job: " + orgId + "/" + jobId);
    }
    item.setJobStatus(JobStatus.PENDING);
    item.setSourceId(null);
    item.setAssignedAt(null);
    item.setAttemptCount(item.getAttemptCount() + 1);
    updatePendingJob(item);
  }

  @Override
  public PendingJob updateJobStatus(String orgId, String jobId, JobStatus status)
      throws IllegalJobStateTransition {
    var item = getPendingJob(orgId, jobId).orElse(null);
    if (item == null) return null;
    item.setJobStatus(status);
    updatePendingJob(item);
    return item;
  }

  @Override
  public List<PendingJob> getJobsBySourceAndStatus(
      String orgId, String source, JobStatus status, int limit) {
    var index = table.index(TablesAndIndexes.PENDING_JOBS_BY_SOURCE_STATUS_GSI);
    var query =
        index.query(
            QueryEnhancedRequest.builder()
                .limit(10)
                .queryConditional(
                    QueryConditional.keyEqualTo(
                        Key.builder()
                            .partitionValue(orgId + "#" + source + "#" + status.name())
                            .build()))
                .build());
    return Lists.newArrayList(new FlatteningIterator<>(query.iterator())).stream()
        .map(DdbMapper::toApi)
        .toList();
  }

  @Override
  public PendingJob updateJobResult(String orgId, String jobId, String resultData)
      throws IllegalJobStateTransition {
    var item = getPendingJob(orgId, jobId);
    if (item.isEmpty()) {
      throw new JobNotFoundException("Job not found for result update: " + orgId + "/" + jobId);
    }
    var location = this.resultUploader.uploadResult(orgId, jobId, resultData);
    var job = item.get();
    job.setResultLocation(location);
    job.setJobStatus(JobStatus.COMPLETED);
    updatePendingJob(job);
    return job;
  }

  @Override
  public PendingJob updateJobError(String orgId, String jobId, String errorData)
      throws IllegalJobStateTransition {
    var job =
        getPendingJob(orgId, jobId)
            .orElseThrow(
                () ->
                    new JobNotFoundException(
                        "Job not found for result update: " + orgId + "/" + jobId));
    var location = this.resultUploader.uploadResult(orgId, jobId, errorData);
    job.setErrorLocation(location);
    job.setJobStatus(JobStatus.FAILED);
    updatePendingJob(job);
    return job;
  }

  @Override
  public Optional<QueryResult> getRawResult(String orgId, String jobId) {
    var job =
        getPendingJob(orgId, jobId)
            .orElseThrow(
                () ->
                    new JobNotFoundException(
                        "Job not found for raw result: " + orgId + "/" + jobId));
    if (job.getJobStatus() != JobStatus.COMPLETED && job.getJobStatus() != JobStatus.FAILED) {
      return Optional.empty();
    }
    var rawResult = this.resultUploader.getRawResult(orgId, jobId);
    var deserialized = gson.fromJson(rawResult, QueryResult.class);
    return Optional.of(deserialized);
  }
}
