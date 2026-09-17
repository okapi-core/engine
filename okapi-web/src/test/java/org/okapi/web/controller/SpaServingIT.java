/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.BadRequestException;
import org.okapi.web.OkapiWebApp;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.security.SessionAuthenticationService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = OkapiWebApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpaServingIT {

  private static final OkHttpClient CLIENT = new OkHttpClient();
  private static final MediaType JSON = MediaType.parse("application/json");

  @LocalServerPort int port;

  @MockitoBean UserManager userManager;
  @MockitoBean SessionAuthenticationService sessionAuthenticationService;

  @Test
  void getRootServesIndexWithoutAuthentication() throws IOException {
    assertIndexServed("/");
  }

  @Test
  void getSpaRouteServesIndexWithoutAuthentication() throws IOException {
    assertIndexServed("/main/dashboards");
    assertIndexServed("/main/dashboards/demo-dashboard/v1/edit");
  }

  @Test
  void getApiRoutesRequireAuthentication() throws IOException {
    assertUnauthorized("/api/v1/users/profile");
    assertUnauthorized("/api/v1/query?query=up");
  }

  @Test
  void publicAuthApiRoutesAreNotRejectedBySecurity() throws IOException {
    doThrow(new BadRequestException("signup reached controller"))
        .when(userManager)
        .signupWithEmailPassword(any());
    when(sessionAuthenticationService.authenticate(any(SignInRequest.class), any(), any()))
        .thenThrow(new BadRequestException("signin reached controller"));

    assertJsonPostIsBadRequest(
        "/api/v1/users",
        """
        {"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","password":"secret123"}
        """);
    assertJsonPostIsBadRequest(
        "/api/v1/users/sign-in",
        """
        {"email":"ada@example.com","password":"secret123"}
        """);
  }

  @Test
  void nonMainRoutesDoNotServeIndexWithoutAuthentication() throws IOException {
    assertUnauthorized("/login");
    assertUnauthorized("/some-random-route");
  }

  private void assertUnauthorized(String path) throws IOException {
    var request = new Request.Builder().url("http://localhost:" + port + path).get().build();

    try (var response = CLIENT.newCall(request).execute()) {
      assertEquals(401, response.code());
    }
  }

  private void assertJsonPostIsBadRequest(String path, String json) throws IOException {
    var request =
        new Request.Builder()
            .url("http://localhost:" + port + path)
            .post(RequestBody.create(json, JSON))
            .build();

    try (var response = CLIENT.newCall(request).execute()) {
      assertEquals(400, response.code());
    }
  }

  private void assertIndexServed(String path) throws IOException {
    var request = new Request.Builder().url("http://localhost:" + port + path).get().build();

    try (var response = CLIENT.newCall(request).execute()) {
      assertEquals(200, response.code());
      assertNotNull(response.body());
      assertEquals(expectedIndexContents(), response.body().string());
    }
  }

  private static String expectedIndexContents() throws IOException {
    return Files.readString(
        repoRoot().resolve("okapi-web/src/main/resources/public/index.html"),
        StandardCharsets.UTF_8);
  }

  private static Path repoRoot() {
    var current = Path.of("").toAbsolutePath();
    while (current != null) {
      if (Files.exists(current.resolve("okapi-web/src/main/resources/public/index.html"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Unable to locate okapi repository root");
  }
}
