/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.staticserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.okapi.headers.CookiesAndHeaders;
import org.okapi.staticserver.AuthenticationServer;
import org.okapi.staticserver.CookieConfig;
import org.okapi.staticserver.ProxyServer;
import org.okapi.staticserver.exceptions.BadRequestException;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.TokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ApiProxyController {

  @Autowired AuthenticationServer authenticationServer;
  @Autowired ProxyServer proxyServer;

  @Value("${cookies.secureOnly}")
  boolean secureOnlyCookies;

  @Value("${cookies.httpOnly}")
  boolean httpOnlyCookies;

  @PostMapping("/auth/sign-up")
  public ResponseEntity<String> signUp(@RequestBody CreateUserRequest createUserRequest)
      throws IOException, BadRequestException {
    var response = authenticationServer.signUp(createUserRequest);
    var tokenResponse = response.getData();
    HttpCookie httpCookie =
        ResponseCookie.from(CookiesAndHeaders.COOKIE_LOGIN_TOKEN, tokenResponse.get().getToken())
            .path("/")
            .httpOnly(httpOnlyCookies)
            .secure(secureOnlyCookies)
            .maxAge(CookieConfig.ACCESS_TOKEN_EXPIRY_DURATION)
            .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, httpCookie.toString()).build();
  }

  @PostMapping("/auth/sign-in")
  public ResponseEntity<String> signIn(@RequestBody CreateUserRequest createUserRequest)
      throws IOException, BadRequestException {
    var response = authenticationServer.signIn(createUserRequest);
    var tokenResponse = response.getData();
    HttpCookie httpCookie =
        ResponseCookie.from(CookiesAndHeaders.COOKIE_LOGIN_TOKEN, tokenResponse.get().getToken())
            .path("/")
            .httpOnly(httpOnlyCookies)
            .secure(secureOnlyCookies)
            .maxAge(CookieConfig.ACCESS_TOKEN_EXPIRY_DURATION)
            .build();
    return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, httpCookie.toString()).build();
  }

  @PostMapping("/auth/temp-token")
  public ResponseEntity<TokenResponse> tempToken(
      @CookieValue(CookiesAndHeaders.COOKIE_LOGIN_TOKEN) String loginToken)
      throws IOException, BadRequestException {
    var response = authenticationServer.tempToken(loginToken);
    var tokenResponse = response.getData();
    return ResponseEntity.ok().body(tokenResponse.get());
  }

  @GetMapping("/auth/ping")
  public ResponseEntity<Void> ping(
      @CookieValue(CookiesAndHeaders.COOKIE_LOGIN_TOKEN) String loginToken)
      throws IOException, BadRequestException {
    authenticationServer.ping(loginToken);
    return ResponseEntity.ok().build();
  }

  // should be the last controller in the chain
  @RequestMapping("/**")
  public ResponseEntity<byte[]> proxyRequest(
      @CookieValue(value = CookiesAndHeaders.COOKIE_LOGIN_TOKEN, required = false)
          String loginToken,
      HttpServletRequest request)
      throws IOException {
    return proxyServer.proxyRequest(loginToken, request);
  }
}
