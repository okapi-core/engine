/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = NoAutomaticMigrationIT.TestApplication.class)
class NoAutomaticMigrationIT {
  private static final String ADMIN_URL =
      System.getenv()
          .getOrDefault("TEST_POSTGRES_ADMIN_URL", "jdbc:postgresql://127.0.0.1:5432/okapi_oscar");
  private static final String ADMIN_USER =
      System.getenv().getOrDefault("TEST_POSTGRES_ADMIN_USER", "okapi_oscar_user_admin");
  private static final String ADMIN_PASSWORD =
      System.getenv().getOrDefault("TEST_POSTGRES_ADMIN_PASSWORD", "okapi_oscar_password");
  private static final String APP_URL =
      System.getenv()
          .getOrDefault("OKAPI_WEB_DB_URL", "jdbc:postgresql://127.0.0.1:5432/okapi_oscar");
  private static final String APP_USER =
      System.getenv().getOrDefault("OKAPI_WEB_DB_USER", "okapi_web_user");
  private static final String APP_PASSWORD =
      System.getenv().getOrDefault("OKAPI_WEB_DB_PASSWORD", "okapi_web_password");
  private static final String SCHEMA =
      "startup_test_" + UUID.randomUUID().toString().replace("-", "");

  static {
    executeAdmin("CREATE SCHEMA " + SCHEMA);
    executeAdmin("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + quoteIdentifier(APP_USER));
  }

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("okapi.data.pg.url", () -> withSchema(APP_URL, SCHEMA));
    registry.add("okapi.data.pg.username", () -> APP_USER);
    registry.add("okapi.data.pg.password", () -> APP_PASSWORD);
  }

  @AfterAll
  static void dropSchema() {
    executeAdmin("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
  }

  @Test
  void applicationStartupDoesNotCreateDataTables() throws Exception {
    try (var connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
        var statement =
            connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = 'users')")) {
      statement.setString(1, SCHEMA);
      try (var result = statement.executeQuery()) {
        result.next();
        assertFalse(result.getBoolean(1));
      }
    }
  }

  private static void executeAdmin(String sql) {
    try (var connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
        var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (Exception e) {
      throw new IllegalStateException("Could not prepare PostgreSQL startup test schema", e);
    }
  }

  private static String withSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }

  private static String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  @SpringBootApplication
  static class TestApplication {}
}
