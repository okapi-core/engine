/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dispatcher;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.okapi.agent.dto.QueryResult;
import org.okapi.data.dao.PendingJobsDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.DataSourceQuery;
import org.okapi.data.model.JobStatus;
import org.okapi.data.model.PendingJob;
import org.okapi.web.auth.AbstractIT;
import org.okapi.web.auth.OrgManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.service.federation.dispatcher.PendingJobPoller;
import org.okapi.web.service.federation.dispatcher.UniversalJobId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.web.auth.TestCommons.addToOrg;

@SpringBootTest
@ActiveProfiles("test")
@Disabled
public class PendingJobPollerIT extends AbstractIT {

  @Autowired private PendingJobsDao pendingJobsDao;
  @Autowired private PendingJobPoller pendingJobPoller;
  @Autowired private UserManager userManager;
  @Autowired private OrgManager orgManager;
  @Autowired private UsersDao usersDao;
  @Autowired private RelationGraphDao relationGraphDao;
  @Autowired private TokenManager tokenManager;

  private String orgId;
  private String userId;
  private String sourceId;
  private Gson gson;

  @BeforeEach
  void setupEach() throws Exception {
    super.setup();
    gson = new Gson();
    var email = dedup("pendingjobpoller@test.com", this.getClass());
    var password = "pw";
    try {
      userManager.signupWithEmailPassword(new CreateUserRequest("Poller", "User", email, password));
    } catch (Exception ignored) {
    }
    var loginToken = userManager.signInWithEmailPassword(new SignInRequest(email, password));
    orgId = orgManager.listOrgs(loginToken).getOrgs().get(0).getOrgId();
    addToOrg(usersDao, relationGraphDao, orgId, email, true);
    userId = tokenManager.getUserId(loginToken);
    sourceId = "poller-source-" + UUID.randomUUID();
  }

  @Test
  void pollerCompletesOnSuccess() throws Exception {
    var jobId = UUID.randomUUID().toString();
    createPendingJob(jobId);

    var future = pendingJobPoller.poll(new UniversalJobId(orgId, jobId));
    assertFalse(future.isDone(), "Future should not be completed immediately");

    pendingJobsDao.updateJobStatus(orgId, jobId, JobStatus.IN_PROGRESS);
    var resultPayload = QueryResult.ofData("ok!");
    pendingJobsDao.updateJobResult(orgId, jobId, gson.toJson(resultPayload));

    var result = future.get(5, TimeUnit.SECONDS);
    assertNotNull(result);
    assertNull(result.error());
    assertEquals("ok!", result.data());
  }

  @Test
  void pollerCompletesOnFailure() throws Exception {
    var jobId = UUID.randomUUID().toString();
    createPendingJob(jobId);

    var future = pendingJobPoller.poll(new UniversalJobId(orgId, jobId));
    assertFalse(future.isDone(), "Future should not be completed immediately");

    pendingJobsDao.updateJobStatus(orgId, jobId, JobStatus.IN_PROGRESS);
    var errorPayload = QueryResult.ofError("boom");
    pendingJobsDao.updateJobError(orgId, jobId, gson.toJson(errorPayload));

    var result = future.get(5, TimeUnit.SECONDS);
    assertNotNull(result);
    assertNull(result.data());
    assertNotNull(result.error());
    assertEquals("boom", result.error());
  }

  private void createPendingJob(String jobId) {
    var pendingJob =
        PendingJob.builder()
            .orgId(orgId)
            .jobId(jobId)
            .jobStatus(JobStatus.PENDING)
            .sourceId(sourceId)
            .query(new DataSourceQuery("dummy-query", sourceId))
            .attemptCount(0)
            .createdAt(System.currentTimeMillis())
            .build();
    pendingJobsDao.createPendingJob(pendingJob);
  }
}
