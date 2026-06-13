/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data;

import java.net.URI;
import java.util.List;
import org.okapi.data.migrations.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class CreateDynamoDBTables {
  public static void createTables(DynamoDbClient dynamoDBClient) {
    var specs =
        List.<TableSpec<?>>of(
            new DashboardTableSpec(),
            new DashboardRowsTableSpec(),
            new DashboardPanelsTableSpec(),
            new OrgsTableSpec(),
            new RelationGraphDdbSpec(),
            new UsersTableSpec(),
            new FederatedSourceTable(),
            new UserEntityRelationsTableSpec(),
            new InfraEntityNodesTableSpec(),
            new PendingJobsTableSpec(),
            new TokenMetaTableSpec(),
            new DashboardVarsTableSpec(),
            new DashboardVersionsTableSpec());
    for (var spec : specs) {
      spec.create(dynamoDBClient);
    }
  }

  public static DynamoDbClient getDynamoDBClient(String endpoint, String region) {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .build();
  }

  public static void main(String[] args) {
    var env = args[0];
    String endpoint =
        System.getenv().getOrDefault("OKAPI_AWS_ENDPOINT", "http://127.0.0.1:4566");
    if (env.equals("prod")) {
      endpoint = "https://dynamodb.eu-west-2.amazonaws.com";
    }
    var dynamoDb = getDynamoDBClient(endpoint, "us-west-2");
    createTables(dynamoDb);
  }
}
