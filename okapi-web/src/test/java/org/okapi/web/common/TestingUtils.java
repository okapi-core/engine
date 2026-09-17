/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.common;

import com.google.gson.Gson;
import java.net.URI;
import mockwebserver3.MockResponse;

public class TestingUtils {
  public static final <T> MockResponse makeJsonResponse(T dto) {
    var gson = new Gson();
    var payload = gson.toJson(dto);
    return new MockResponse.Builder()
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .body(payload)
        .build();
  }

  public static final int getPort(String endpoint) {
    return URI.create(endpoint).getPort();
  }
}
