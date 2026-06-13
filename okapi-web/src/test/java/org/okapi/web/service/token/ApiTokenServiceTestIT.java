/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.token;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.web.auth.TestCommons.addToOrg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.TokenStatus;
import org.okapi.web.auth.AbstractIT;
import org.okapi.web.auth.OrgManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.dtos.token.ApiToken;
import org.okapi.web.dtos.token.UpdateTokenRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class ApiTokenServiceTestIT extends AbstractIT {

  @Autowired private ApiTokenService apiTokenService;
  @Autowired private UserManager userManager;
  @Autowired private OrgManager orgManager;
  @Autowired private TokenManager tokenManager;
  @Autowired private UsersDao usersDao;
  @Autowired private RelationGraphDao relationGraphDao;

  private String email;
  private String loginToken;
  private String tempToken;
  private String orgId;

  @BeforeEach
  void setupEach() throws Exception {
    super.setup();
    email = dedup("apitoken@test.com", this.getClass());
    try {
      userManager.signupWithEmailPassword(new CreateUserRequest("Api", "Tester", email, "pw"));
    } catch (Exception ignored) {
    }
    loginToken = userManager.signInWithEmailPassword(new SignInRequest(email, "pw"));
    var myOrgs = orgManager.listOrgs(loginToken);
    orgId = myOrgs.getOrgs().get(0).getOrgId();
    addToOrg(usersDao, relationGraphDao, orgId, email, true);
    tempToken = tokenManager.issueTemporaryToken(loginToken, orgId);
  }

  @Test
  void api_token_lifecycle() throws Exception {
    var initialList = apiTokenService.listTokens(tempToken);
    assertNotNull(initialList);
    assertTrue(initialList.getTokens().isEmpty(), "Expected no tokens initially");

    ApiToken created = apiTokenService.createApiSourceToken(tempToken);
    assertNotNull(created);
    assertNotNull(created.getToken(), "New API token should have a value");

    var afterCreateList = apiTokenService.listTokens(tempToken);
    assertEquals(1, afterCreateList.getTokens().size(), "Expected one token after creation");
    var tokenMeta = afterCreateList.getTokens().get(0);
    assertEquals(TokenStatus.ACTIVE.name(), tokenMeta.getTokenStatus());

    var updateReq = new UpdateTokenRequest(tokenMeta.getTokenId(), TokenStatus.INACTIVE.name());
    var updated = apiTokenService.updateToken(tempToken, updateReq);
    assertEquals(TokenStatus.INACTIVE.name(), updated.getTokenStatus());

    var afterUpdateList = apiTokenService.listTokens(tempToken);
    assertTrue(afterUpdateList.getTokens().isEmpty(), "Inactive tokens should not be listed");
  }
}
