/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import static org.okapi.data.dto.TablesAndIndexes.DASHBOARD_PANELS_TABLE;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.dto.DashboardPanel;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

public class DashboardPanelDaoDdbImpl implements DashboardPanelDao {

  private final DynamoDbTable<DashboardPanel> table;

  @Inject
  public DashboardPanelDaoDdbImpl(DynamoDbEnhancedClient enhancedClient) {
    this.table =
        enhancedClient.table(DASHBOARD_PANELS_TABLE, TableSchema.fromBean(DashboardPanel.class));
  }

  @Override
  public void save(
      String orgId,
      String dashboardId,
      String rowId,
      String versionId,
      org.okapi.data.model.DashboardPanel panel) {
    var orgRowId = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId, rowId);
    var stored = DdbMapper.toDdb(panel);
    stored.setOrgPanelHashKey(orgRowId);
    table.putItem(stored);
  }

  @Override
  public void delete(
      String orgId, String dashboardId, String rowId, String versionId, String panelId) {
    var orgRowId = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId, rowId);
    table.deleteItem(Key.builder().partitionValue(orgRowId).sortValue(panelId).build());
  }

  @Override
  public Optional<org.okapi.data.model.DashboardPanel> get(
      String orgId, String dashboardId, String rowId, String versionId, String panelId) {
    var orgRowId = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId, rowId);
    var item = table.getItem(Key.builder().partitionValue(orgRowId).sortValue(panelId).build());
    return Optional.ofNullable(DdbMapper.toApi(item));
  }

  @Override
  public List<org.okapi.data.model.DashboardPanel> getAll(
      String orgId, String dashboardId, String rowId, String versionId) {
    var orgRowId = ResourceIdCreator.createResourceId(orgId, dashboardId, versionId, rowId);
    var query =
        table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(orgRowId).build()))
                .build());
    return Lists.newArrayList(new FlatteningIterator<>(query.iterator())).stream()
        .map(DdbMapper::toApi)
        .toList();
  }
}
