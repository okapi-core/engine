/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import java.util.concurrent.Callable;
import org.okapi.data.pg.migrations.PostgresMigrator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(
    name = "pg-validate",
    description = "Validate okapi-web PostgreSQL migrations and reject pending migrations.")
public class PgValidateCommand implements Callable<Integer> {
  @Mixin private PgConnectionOptions connection;

  @Override
  public Integer call() {
    PostgresMigrator.validate(connection.url(), connection.user(), connection.password());
    System.out.println("PostgreSQL migrations are valid and current.");
    return 0;
  }
}
