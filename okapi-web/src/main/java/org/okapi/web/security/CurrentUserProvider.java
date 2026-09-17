/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.security;

import org.okapi.exceptions.UnAuthorizedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

  public OkapiUserPrincipal requireUser() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof OkapiUserPrincipal principal)) {
      throw new UnAuthorizedException();
    }
    return principal;
  }

  public String userId() {
    return requireUser().userId();
  }
}
