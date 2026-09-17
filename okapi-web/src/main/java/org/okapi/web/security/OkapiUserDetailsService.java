/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.security;

import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.UsersDao;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OkapiUserDetailsService implements UserDetailsService {
  private final UsersDao usersDao;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return usersDao
        .getWithEmail(email)
        .map(OkapiUserPrincipal::from)
        .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
  }
}
