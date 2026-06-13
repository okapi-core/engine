/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.migrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class PostgresMigratorIT {
  private static final String ADMIN_URL =
      System.getenv()
          .getOrDefault(
              "OKAPI_WEB_DB_MIGRATION_URL", "jdbc:postgresql://127.0.0.1:5432/okapi_oscar");
  private static final String ADMIN_USER =
      System.getenv().getOrDefault("OKAPI_WEB_DB_MIGRATION_USER", "okapi_oscar_user_admin");
  private static final String ADMIN_PASSWORD =
      System.getenv().getOrDefault("OKAPI_WEB_DB_MIGRATION_PASSWORD", "okapi_oscar_password");

  @Test
  void migratesAndValidatesARealPostgresSchema() throws Exception {
    var schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
    try {
      executeAdmin("CREATE SCHEMA " + schema);
      var schemaUrl = withSchema(ADMIN_URL, schema);

      assertThrows(
          FlywayException.class,
          () -> PostgresMigrator.validate(schemaUrl, ADMIN_USER, ADMIN_PASSWORD));
      assertEquals(2, PostgresMigrator.migrate(schemaUrl, ADMIN_USER, ADMIN_PASSWORD));
      assertEquals(0, PostgresMigrator.migrate(schemaUrl, ADMIN_USER, ADMIN_PASSWORD));
      PostgresMigrator.validate(schemaUrl, ADMIN_USER, ADMIN_PASSWORD);
      assertTrue(tableExists(schema));

      executeAdmin(
          "UPDATE "
              + schema
              + ".flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
      assertThrows(
          FlywayException.class,
          () -> PostgresMigrator.validate(schemaUrl, ADMIN_USER, ADMIN_PASSWORD));
    } finally {
      executeAdmin("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  private static boolean tableExists(String schema) throws Exception {
    try (var connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
        var statement =
            connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = 'users')")) {
      statement.setString(1, schema);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  private static void executeAdmin(String sql) throws Exception {
    try (var connection = DriverManager.getConnection(ADMIN_URL, ADMIN_USER, ADMIN_PASSWORD);
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String withSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
  }
}
