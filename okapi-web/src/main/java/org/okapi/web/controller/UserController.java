/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.GetUserProfileResponse;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.dtos.auth.UpdateUserRequest;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.security.SessionAuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {

  @Autowired UserManager userManager;
  @Autowired SessionAuthenticationService sessionAuthenticationService;
  @Autowired CurrentUserProvider currentUserProvider;

  @PostMapping("/users")
  public ResponseEntity<Void> createUser(
      @RequestBody CreateUserRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse)
      throws BadRequestException {
    userManager.signupWithEmailPassword(request);
    sessionAuthenticationService.authenticate(
        request.getEmail(), request.getPassword(), servletRequest, servletResponse);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/sign-in")
  public ResponseEntity<Void> signInWithPass(
      @RequestBody @Valid SignInRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse)
      throws UnAuthorizedException {
    sessionAuthenticationService.authenticate(request, servletRequest, servletResponse);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/users/profile")
  public GetUserProfileResponse getUserProfile() throws UnAuthorizedException {
    return userManager.getUserProfileRes(currentUserProvider.userId());
  }

  @PostMapping("/users/profile/update")
  public GetUserProfileResponse updateUserProfile(@RequestBody UpdateUserRequest updateUserRequest)
      throws UnAuthorizedException, BadRequestException {
    return userManager.updateProfile(currentUserProvider.userId(), updateUserRequest);
  }
}
