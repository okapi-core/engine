/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.migrations;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

public final class PostgresMigrator {
  private static final String MIGRATION_LOCATION = "classpath:db/migration";

  private PostgresMigrator() {}

  public static int migrate(String url, String username, String password) {
    return flyway(url, username, password).migrate().migrationsExecuted;
  }

  public static void validate(String url, String username, String password) {
    var flyway = flyway(url, username, password);
    flyway.validate();
    var pending = flyway.info().pending();
    if (pending.length > 0) {
      throw new FlywayException(
          "Database has " + pending.length + " pending PostgreSQL migration(s)");
    }
  }

  private static Flyway flyway(String url, String username, String password) {
    return Flyway.configure()
        .dataSource(url, username, password)
        .locations(MIGRATION_LOCATION)
        .baselineOnMigrate(true)
        .baselineVersion("0")
        .load();
  }
}
