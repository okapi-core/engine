/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.dtos.auth.SignInRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionAuthenticationService {
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

  public OkapiUserPrincipal authenticate(
      SignInRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    return authenticate(request.getEmail(), request.getPassword(), servletRequest, servletResponse);
  }

  public OkapiUserPrincipal authenticate(
      String email,
      String password,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    try {
      var authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(email, password));
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, servletRequest, servletResponse);
      return (OkapiUserPrincipal) authentication.getPrincipal();
    } catch (AuthenticationException e) {
      throw new UnAuthorizedException("Invalid credentials");
    }
  }
}
