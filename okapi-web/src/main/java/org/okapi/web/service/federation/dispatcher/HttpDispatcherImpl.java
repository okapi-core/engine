/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.federation.dispatcher;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.okapi.agent.dto.PendingJob;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.model.JobStatus;
import org.springframework.stereotype.Service;

@Service
public class HttpDispatcherImpl implements JobDispatcher {
  PendingJobsDao jobsDao;
  PendingJobPoller pendingJobPoller;

  public HttpDispatcherImpl(PendingJobsDao jobsDao, PendingJobPoller pendingJobPoller) {
    this.jobsDao = jobsDao;
    this.pendingJobPoller = pendingJobPoller;
  }

  @Override
  public CompletableFuture<QueryResult> dispatchJob(String orgId, PendingJob job) {
    var pendingJob =
        org.okapi.data.model.PendingJob.builder()
            .orgId(orgId)
            .jobId(job.getJobId())
            .sourceId(job.getSourceId())
            .query(
                new org.okapi.data.model.DataSourceQuery(
                    job.getSpec().serializedQuery(), job.getSourceId()))
            .jobStatus(JobStatus.PENDING)
            .attemptCount(0)
            .createdAt(Instant.now().toEpochMilli())
            .build();
    jobsDao.createPendingJob(pendingJob);
    UniversalJobId universalJobId = new UniversalJobId(orgId, job.getJobId());
    return pendingJobPoller.poll(universalJobId);
  }
}
