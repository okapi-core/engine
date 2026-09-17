/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.okapi.web.OkapiWebApp;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = OkapiWebApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"okapi.org.orgId=web-api-it", "okapi.org.orgName=Web API IT"})
@ActiveProfiles("test")
abstract class WebApiITSupport {
  private static final OkHttpClient CLIENT = new OkHttpClient();
  private static final MediaType JSON = MediaType.parse("application/json");

  @LocalServerPort private int port;

  protected final ObjectMapper objectMapper = new ObjectMapper();

  protected Response postJsonResponse(String path, Object body) throws IOException {
    var request = new Request.Builder().url(url(path)).post(jsonBody(body)).build();
    return CLIENT.newCall(request).execute();
  }

  protected JsonNode getJson(String path, String sessionCookie, int expectedStatus)
      throws IOException {
    var request =
        new Request.Builder().url(url(path)).header("Cookie", sessionCookie).get().build();
    return executeJson(request, expectedStatus);
  }

  protected JsonNode getJson(String path, int expectedStatus) throws IOException {
    var request = new Request.Builder().url(url(path)).get().build();
    return executeJson(request, expectedStatus);
  }

  protected String sessionCookie(Response response) throws IOException {
    assertEquals(204, response.code(), responseBody(response));
    var cookie = response.header("Set-Cookie");
    assertNotNull(cookie);
    assertFalse(cookie.isBlank());
    return cookie.split(";", 2)[0];
  }

  protected String uniqueEmail(String prefix) {
    return prefix + "-" + java.util.UUID.randomUUID() + "@okapi.test";
  }

  private JsonNode executeJson(Request request, int expectedStatus) throws IOException {
    try (var response = CLIENT.newCall(request).execute()) {
      var body = responseBody(response);
      assertEquals(expectedStatus, response.code(), body);
      return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }
  }

  private RequestBody jsonBody(Object body) throws IOException {
    return RequestBody.create(objectMapper.writeValueAsString(body), JSON);
  }

  private String responseBody(Response response) throws IOException {
    return response.body() == null ? "" : response.body().string();
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }
}
