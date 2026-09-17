/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UserOrgFlowIT extends WebApiITSupport {
  private static final String ORG_ID = "web-api-it";
  private static final String ORG_NAME = "Web API IT";

  @Test
  void signedUpUserCanReadAssignedOrg() throws Exception {
    var email = uniqueEmail("user-org-flow");
    var sessionCookie =
        signUp(
            Map.of(
                "firstName", "Oscar",
                "lastName", "Okapi",
                "email", email,
                "password", "password123"));

    var profile = getJson("/api/v1/users/profile", sessionCookie, 200);
    assertEquals("Oscar", profile.path("firstName").asText());
    assertEquals("Okapi", profile.path("lastName").asText());
    assertEquals(email, profile.path("email").asText());
    assertEquals(ORG_ID, profile.path("orgSummary").path("orgId").asText());
    assertEquals(ORG_NAME, profile.path("orgSummary").path("orgName").asText());

    var org = getJson("/api/v1/orgs/" + ORG_ID, sessionCookie, 200);
    assertEquals(ORG_ID, org.path("orgId").asText());
    assertEquals(ORG_NAME, org.path("orgName").asText());
    assertMember(org.path("members"), email, "Oscar", "Okapi");
  }

  @Test
  void unauthenticatedUserCannotReadOrg() throws Exception {
    getJson("/api/v1/orgs/" + ORG_ID, 401);
  }

  private String signUp(Map<String, String> body) throws Exception {
    try (var response = postJsonResponse("/api/v1/users", body)) {
      return sessionCookie(response);
    }
  }

  private void assertMember(JsonNode members, String email, String firstName, String lastName) {
    assertFalse(members.isMissingNode(), "members should be present");
    for (var member : members) {
      if (email.equals(member.path("email").asText())) {
        assertEquals(firstName, member.path("firstName").asText());
        assertEquals(lastName, member.path("lastName").asText());
        assertFalse(member.path("userId").asText().isBlank());
        return;
      }
    }
    fail("Expected org member with email " + email + " in " + members);
  }
}
