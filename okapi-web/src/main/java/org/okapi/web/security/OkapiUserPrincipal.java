/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.security;

import java.util.Collection;
import java.util.List;
import org.okapi.data.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record OkapiUserPrincipal(
    String userId,
    String email,
    String password,
    boolean enabled,
    Collection<GrantedAuthority> authorities)
    implements UserDetails {

  public static OkapiUserPrincipal from(User user) {
    return new OkapiUserPrincipal(
        user.getUserId(),
        user.getEmail(),
        user.getHashedPassword(),
        user.getStatus() == User.Status.ACTIVE,
        List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getPassword() {
    return password;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return authorities;
  }
}
