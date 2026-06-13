/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.pendingjob;

import java.util.List;
import lombok.AllArgsConstructor;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;
import org.okapi.web.spring.config.FederationAgentCfg;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class DbFetchAssigner implements PendingJobAssigner {

  PendingJobsDao pendingJobsDao;
  FederationAgentCfg agentCfg;

  @Override
  public List<PendingJob> getPendingJobs(String orgId, List<String> sources, int maxJobs) {
    var results = new java.util.ArrayList<PendingJob>();
    for (var source : sources) {
      var pending =
          pendingJobsDao.getJobsBySourceAndStatus(
              orgId, source, JobStatus.PENDING, agentCfg.getMaxJobsPerDispatch());
      results.addAll(pending);
    }
    return results.size() > maxJobs ? results.subList(0, maxJobs) : results;
  }
}
