/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresConnectivityTest {
  private static final String URL =
      System.getenv()
          .getOrDefault(
              "OKAPI_WEB_DB_URL",
              "jdbc:postgresql://127.0.0.1:5432/okapi_oscar?currentSchema=okapi_web");
  private static final String USER =
      System.getenv().getOrDefault("OKAPI_WEB_DB_USER", "okapi_web_user");
  private static final String PASSWORD =
      System.getenv().getOrDefault("OKAPI_WEB_DB_PASSWORD", "okapi_web_password");

  @Test
  void connectsToRealPostgresAndRollsBackWrites() throws Exception {
    try (var connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
      connection.setAutoCommit(false);

      try (var schemaQuery = connection.createStatement();
          var result = schemaQuery.executeQuery("select current_schema(), version()")) {
        result.next();
        assertEquals("okapi_web", result.getString(1));
        assertTrue(result.getString(2).startsWith("PostgreSQL 16"));
      }

      try (var create = connection.createStatement()) {
        create.execute("create table pg_connectivity_test (id uuid primary key)");
      }

      var id = UUID.randomUUID();
      try (var insert =
          connection.prepareStatement("insert into pg_connectivity_test(id) values (?)")) {
        insert.setObject(1, id);
        assertEquals(1, insert.executeUpdate());
      }

      try (var query =
          connection.prepareStatement("select count(*) from pg_connectivity_test where id = ?")) {
        query.setObject(1, id);
        try (var result = query.executeQuery()) {
          result.next();
          assertEquals(1, result.getInt(1));
        }
      }

      connection.rollback();
    }
  }
}
