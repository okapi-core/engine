/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import java.util.concurrent.Callable;
import org.okapi.ops.ch.CreateChTablesSpec;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "ch-migrate",
    description = "Create ClickHouse database/tables.",
    header = "Example: okapi-ops ch-migrate --host 127.0.0.1 --port 8123")
public class ChMigrateCommand implements Callable<Integer> {
  @Option(names = "--host", description = "ClickHouse host.", defaultValue = "127.0.0.1")
  private String host;

  @Option(names = "--port", description = "ClickHouse HTTP port.", defaultValue = "9000")
  private int port;

  @Option(names = "--secure", description = "Use HTTPS for ClickHouse.", defaultValue = "false")
  private boolean secure;

  @Option(names = "--user", description = "ClickHouse username.", defaultValue = "default")
  private String user;

  @Option(names = "--password", description = "ClickHouse password.", defaultValue = "")
  private String password;

  @Override
  public Integer call() {
    try (Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, secure)
            .setUsername(user)
            .setPassword(password)
            .build()) {
      waitForReady(client, 30_000L);
      CreateChTablesSpec.migrate(client);
    }
    return 0;
  }

  private static void waitForReady(Client client, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (true) {
      try {
        client.queryAll("SELECT 1");
        return;
      } catch (Exception ignored) {
        if (System.currentTimeMillis() >= deadline) {
          throw ignored instanceof RuntimeException
              ? (RuntimeException) ignored
              : new RuntimeException("ClickHouse not ready within timeout", ignored);
        }
        try {
          Thread.sleep(1_000L);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for ClickHouse readiness", ie);
        }
      }
    }
  }
}
