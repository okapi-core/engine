/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ops;

import java.util.concurrent.Callable;
import org.okapi.data.CreateDynamoDBTables;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "ddb-migrate",
    description = "Create DynamoDB tables.",
    header = "Example: okapi-ops ddb-migrate --env local --endpoint http://127.0.0.1:4566")
public class DdbMigrateCommand implements Callable<Integer> {
  @Option(names = "--endpoint", description = "DynamoDB endpoint override.", required = true)
  private String endpoint;

  @Option(names = "--region", description = "AWS region.", defaultValue = "eu-west-2")
  private String region;

  @Override
  public Integer call() {
    EndpointWaiter.waitForEndpoint(endpoint);
    try (var dynamoDb = CreateDynamoDBTables.getDynamoDBClient(endpoint, region)) {
      CreateDynamoDBTables.createTables(dynamoDb);
    }
    return 0;
  }
}
