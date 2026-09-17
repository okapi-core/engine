/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import picocli.CommandLine.Option;

final class PgConnectionOptions {
  @Option(names = "--url", description = "PostgreSQL JDBC URL.")
  private String url;

  @Option(names = "--user", description = "PostgreSQL migration username.")
  private String user;

  @Option(names = "--password", description = "PostgreSQL migration password.")
  private String password;

  String url() {
    return value(url, "OKAPI_WEB_DB_MIGRATION_URL", "OKAPI_WEB_DB_URL");
  }

  String user() {
    return value(user, "OKAPI_WEB_DB_MIGRATION_USER", "OKAPI_WEB_DB_USER");
  }

  String password() {
    return value(password, "OKAPI_WEB_DB_MIGRATION_PASSWORD", "OKAPI_WEB_DB_PASSWORD");
  }

  private static String value(String option, String primaryEnv, String fallbackEnv) {
    if (option != null && !option.isBlank()) {
      return option;
    }
    var primary = System.getenv(primaryEnv);
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    var fallback = System.getenv(fallbackEnv);
    if (fallback != null && !fallback.isBlank()) {
      return fallback;
    }
    throw new IllegalArgumentException(
        "Missing option and environment variable: --"
            + primaryEnv.toLowerCase().replace('_', '-')
            + " / "
            + primaryEnv);
  }
}
