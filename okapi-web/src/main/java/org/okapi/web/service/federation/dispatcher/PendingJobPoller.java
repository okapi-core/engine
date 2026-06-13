/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.federation.dispatcher;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.model.JobStatus;
import org.okapi.web.spring.config.PollingTaskCfg;
import org.springframework.stereotype.Service;

@Service
public class PendingJobPoller {
  public record PollConfig(long delayMs, int maxAttempts) {}

  ScheduledExecutorService scheduler;
  PendingJobsDao jobsDao;
  PollingTaskCfg config;

  public PendingJobPoller(PendingJobsDao jobsDao, PollingTaskCfg config) {
    this.scheduler = Executors.newScheduledThreadPool(config.getThreads());
    this.jobsDao = jobsDao;
    this.config = config;
  }

  public CompletableFuture<QueryResult> poll(UniversalJobId jobId) {
    if (jobsDao.getPendingJob(jobId.orgId(), jobId.jobId()).isEmpty()) {
      throw new IllegalArgumentException(
          "Job with id " + jobId.jobId() + " not found for org " + jobId.orgId());
    }
    PollingTask<QueryResult> pollingTask =
        new PollingTask<>(
            () -> {
              var optionalJob = jobsDao.getPendingJob(jobId.orgId(), jobId.jobId());
              var job =
                  optionalJob.orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Job with id "
                                  + jobId.jobId()
                                  + " not found for org "
                                  + jobId.orgId()));
              if (job.getJobStatus() == JobStatus.COMPLETED) {
                var jobResult = jobsDao.getRawResult(jobId.orgId(), jobId.jobId());
                return new PollingTask.PollStatus<>(PollingTask.POLL_STATUS.DONE, jobResult.get());
              } else if (job.getJobStatus() == JobStatus.FAILED) {
                var jobError = jobsDao.getRawResult(jobId.orgId(), jobId.jobId());
                return new PollingTask.PollStatus<>(PollingTask.POLL_STATUS.DONE, jobError.get());
              } else return new PollingTask.PollStatus<>(PollingTask.POLL_STATUS.PENDING, null);
            },
            new PollingTask.RunConfig(config.getMaxAttempts(), config.getInitialDelayMs()),
            scheduler);
    scheduler.submit(pollingTask);
    return pollingTask.getFuture();
  }

  @PreDestroy
  public void cleanup() {
    scheduler.shutdownNow();
  }
}
