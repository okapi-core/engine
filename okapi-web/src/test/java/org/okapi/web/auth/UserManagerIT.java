/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.dao.UsersDao;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.usermessages.UserFacingMessages;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"okapi.org.orgId=user-manager-it", "okapi.org.orgName=UserManagerIT"})
@ActiveProfiles("test")
@Execution(ExecutionMode.CONCURRENT)
public class UserManagerIT extends AbstractIT {

  @Autowired UserManager userManager;
  @Autowired UsersDao usersDao;

  @BeforeEach
  public void setup() throws UnAuthorizedException {
    super.setup();
  }

  @Test
  public void testSignup() throws BadRequestException, UnAuthorizedException {
    var randomEmail = randomEmail("email");
    var authRequest = new CreateUserRequest("Oscar", "Okapi", randomEmail, "password123");
    userManager.signupWithEmailPassword(authRequest);
    var user = usersDao.getWithEmail(randomEmail);
    assertTrue(user.isPresent());
    assertEquals(randomEmail, user.get().getEmail());
    assertEquals("user-manager-it", user.get().getOrgId());
    var profile = userManager.getUserProfileRes(user.get().getUserId());
    assertNotNull(profile);
    assertEquals("Oscar", profile.getFirstName());
    assertEquals("Okapi", profile.getLastName());
    assertEquals(randomEmail, profile.getEmail());
    assertNotNull(profile.getOrgSummary());
    assertEquals("user-manager-it", profile.getOrgSummary().getOrgId());
    assertEquals("UserManagerIT", profile.getOrgSummary().getOrgName());
    assertTrue(profile.getOrgSummary().getTotalMembers() >= 1);
  }

  @Test
  public void testSignupWithDuplicateEmail() throws BadRequestException {
    var secondEmail = randomEmail("email2");
    var authRequest = new CreateUserRequest("Oscar", "Okapi", secondEmail, "password123");
    userManager.signupWithEmailPassword(authRequest);
    try {
      userManager.signupWithEmailPassword(authRequest);
      fail("Expected duplicate signup to fail");
    } catch (BadRequestException e) {
      assertEquals(UserFacingMessages.USER_ALREADY_EXISTS, e.getMessage());
    }
  }

  private String randomEmail(String localPart) {
    return dedupWithSession(localPart + "@domain.com", this.getClass());
  }
}
