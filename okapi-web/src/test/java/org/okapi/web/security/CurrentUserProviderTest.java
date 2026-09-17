/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.UnAuthorizedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserProviderTest {
  private final CurrentUserProvider provider = new CurrentUserProvider();

  @BeforeEach
  void resetContext() {
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returnsAuthenticatedUserId() {
    var principal = new OkapiUserPrincipal("user-1", "user@example.com", "hash", true, List.of());
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));

    assertEquals("user-1", provider.userId());
  }

  @Test
  void rejectsMissingAuthentication() {
    assertThrows(UnAuthorizedException.class, provider::userId);
  }
}
