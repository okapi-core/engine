/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.tools;

public class GetTempToken {
  public static final String RESERVED_TEST_USER = "test_user@okapiapp.io";
  public static final String RESERVED_TEST_PASSWORD = "Test@1234___";
  public static final String TEST_ENDPOINT =
      System.getenv().getOrDefault("OKAPI_WEB_ENDPOINT", "http://127.0.0.1:9001");

  public static void main(String[] args) {
    var endpoint = args.length > 0 ? args[0] : TEST_ENDPOINT;
    var tokenCliSupport = new TokenCliSupport(endpoint);
    try {
      TokenCliSupport.TempTokenContext context =
          tokenCliSupport.fetchTempToken(RESERVED_TEST_USER, RESERVED_TEST_PASSWORD);
      System.out.println(context.tempToken());
    } catch (Exception e) {
      System.err.println("Failed to fetch temp token: " + e.getMessage());
      System.exit(1);
    }
  }
}
