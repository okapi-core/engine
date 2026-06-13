/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.pendingjob;

import com.google.gson.Gson;
import java.util.List;
import org.okapi.agent.dto.PendingJob;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.model.JobStatus;
import org.okapi.data.exceptions.IllegalJobStateTransition;
import org.okapi.exceptions.BadRequestException;
import org.okapi.web.dtos.pendingjob.GetPendingJobsRequest;
import org.okapi.web.dtos.pendingjob.ListPendingJobsResponse;
import org.okapi.web.dtos.pendingjob.UpdatePendingJobRequest;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.token.Permissions;
import org.okapi.web.service.validation.AuthorizationHeaderValidator;
import org.springframework.stereotype.Service;

@Service
public class PendingJobService {
  PendingJobAssigner jobAssigner;
  AuthorizationHeaderValidator authorizationHeaderValidator;
  PendingJobsDao pendingJobsDao;
  Gson gson = new Gson();

  public PendingJobService(
      PendingJobAssigner jobAssigner,
      AuthorizationHeaderValidator authorizationHeaderValidator,
      PendingJobsDao pendingJobsDao) {
    this.jobAssigner = jobAssigner;
    this.authorizationHeaderValidator = authorizationHeaderValidator;
    this.pendingJobsDao = pendingJobsDao;
  }

  public static final List<String> GET_PENDING_JOBS_REQUIRED_PERMISSIONS =
      List.of(Permissions.AGENT_JOBS_READ, Permissions.AGENT_JOBS_UPDATE);

  public ListPendingJobsResponse getPendingJobs(String apiToken, GetPendingJobsRequest request) {
    var authorizedEntity = authorizationHeaderValidator.getAuthorizedEntity(apiToken);
    authorizedEntity.hasAllPermissions(GET_PENDING_JOBS_REQUIRED_PERMISSIONS);
    var pendingJobs =
        jobAssigner.getPendingJobs(authorizedEntity.orgId(), request.getSources(), 100);
    return new ListPendingJobsResponse(Mappers.mapPendingJobDtosToResponses(pendingJobs));
  }

  public PendingJob updatePendingJob(
      String apiToken, UpdatePendingJobRequest updatePendingJobRequest) {
    var authorizedEntity = authorizationHeaderValidator.getAuthorizedEntity(apiToken);
    authorizedEntity.hasPermission(Permissions.AGENT_JOBS_UPDATE);
    JobStatus translatedStatus =
        switch (updatePendingJobRequest.getStatus()) {
          case COMPLETED -> JobStatus.COMPLETED;
          case FAILED -> JobStatus.FAILED;
          case PENDING -> JobStatus.PENDING;
          case IN_PROGRESS -> JobStatus.IN_PROGRESS;
          case CANCELED -> JobStatus.CANCELLED;
        };
    try {
      var job =
          pendingJobsDao.updateJobStatus(
              authorizedEntity.orgId(), updatePendingJobRequest.getJobId(), translatedStatus);
      return Mappers.mapPendingJobDtoToResponse(job);
    } catch (IllegalJobStateTransition e) {
      throw new BadRequestException("Could not transition job to state: " + e.getMessage());
    }
  }

  public PendingJob submitJobResult(String apiToken, String jobId, QueryResult result)
      throws IllegalJobStateTransition {
    var authorizedEntity = authorizationHeaderValidator.getAuthorizedEntity(apiToken);
    authorizedEntity.hasPermission(Permissions.AGENT_JOBS_UPDATE);
    var resultSerialized = gson.toJson(result);
    var job = pendingJobsDao.updateJobResult(authorizedEntity.orgId(), jobId, resultSerialized);
    return Mappers.mapPendingJobDtoToResponse(job);
  }

  public PendingJob submitPendingJobError(String apiToken, String jobId, QueryResult errorResult)
      throws IllegalJobStateTransition {
    var authorizedEntity = authorizationHeaderValidator.getAuthorizedEntity(apiToken);
    authorizedEntity.hasPermission(Permissions.AGENT_JOBS_UPDATE);
    var errorSerialized = gson.toJson(errorResult);
    var job = pendingJobsDao.updateJobError(authorizedEntity.orgId(), jobId, errorSerialized);
    return Mappers.mapPendingJobDtoToResponse(job);
  }
}
