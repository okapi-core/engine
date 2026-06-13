/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import static org.okapi.data.dto.TablesAndIndexes.DASHBOARD_ROWS_TABLE;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.ddb.CommonQueryPatterns;
import org.okapi.data.dto.DashboardRow;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class DashboardRowDaoDdbImpl implements DashboardRowDao {

  private final DynamoDbTable<DashboardRow> table;
  CommonQueryPatterns<DashboardRow> queryPatterns = new CommonQueryPatterns<>();
  DynamoDbEnhancedClient enhancedClient;

  @Inject
  public DashboardRowDaoDdbImpl(DynamoDbEnhancedClient enhancedClient) {
    this.enhancedClient = enhancedClient;
    this.table =
        enhancedClient.table(DASHBOARD_ROWS_TABLE, TableSchema.fromBean(DashboardRow.class));
  }

  @Override
  public void save(
      String orgId,
      String dashboardId,
      String versionId,
      org.okapi.data.model.DashboardRow row) {
    var orgDashKey = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
    var stored = DdbMapper.toDdb(row);
    stored.setOrgDashKey(orgDashKey);
    table.putItem(stored);
  }

  @Override
  public void delete(String orgId, String dashboardId, String versionId, String rowId) {
    var orgDashHashKey = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
    table.deleteItem(Key.builder().partitionValue(orgDashHashKey).sortValue(rowId).build());
  }

  @Override
  public Optional<org.okapi.data.model.DashboardRow> get(
      String orgId, String dashboardId, String versionId, String rowId) {
    var orgDashHashKey = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
    var item = table.getItem(Key.builder().partitionValue(orgDashHashKey).sortValue(rowId).build());
    return Optional.ofNullable(DdbMapper.toApi(item));
  }

  @Override
  public List<org.okapi.data.model.DashboardRow> getAll(
      String orgId, String dashboardId, String versionId) {
    var orgDashHashKey = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId);
    return this.queryPatterns
        .listByPartitionKey(
            this.enhancedClient, table, AttributeValue.builder().s(orgDashHashKey).build())
        .stream()
        .map(DdbMapper::toApi)
        .toList();
  }
}
