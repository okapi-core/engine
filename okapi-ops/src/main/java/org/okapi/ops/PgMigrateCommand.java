/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import java.util.concurrent.Callable;
import org.okapi.data.pg.migrations.PostgresMigrator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "pg-migrate",
    description = "Apply pending okapi-web PostgreSQL migrations.",
    header = "Example: okapi-ops pg-migrate --url jdbc:postgresql://localhost/okapi")
public class PgMigrateCommand implements Callable<Integer> {
  @Mixin private PgConnectionOptions connection;

  @Override
  public Integer call() {
    int migrations =
        PostgresMigrator.migrate(connection.url(), connection.user(), connection.password());
    System.out.println("Applied " + migrations + " PostgreSQL migration(s).");
    return 0;
  }
}
