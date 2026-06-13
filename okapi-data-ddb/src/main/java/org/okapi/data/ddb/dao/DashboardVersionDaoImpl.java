/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import static org.okapi.data.dto.TablesAndIndexes.DASHBOARD_VERSIONS_TABLE;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.dto.DashboardVersion;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

public class DashboardVersionDaoImpl implements DashboardVersionDao {
  private final DynamoDbTable<DashboardVersion> table;

  @Inject
  public DashboardVersionDaoImpl(DynamoDbEnhancedClient enhancedClient) {
    this.table =
        enhancedClient.table(
            DASHBOARD_VERSIONS_TABLE, TableSchema.fromBean(DashboardVersion.class));
  }

  @Override
  public void save(org.okapi.data.model.DashboardVersion version) {
    table.putItem(DdbMapper.toDdb(version));
  }

  @Override
  public Optional<org.okapi.data.model.DashboardVersion> get(
      String orgId, String dashboardId, String versionId) {
    var key = DashboardVersion.dashboardVersionId(dashboardId, versionId);
    var item = table.getItem(Key.builder().partitionValue(orgId).sortValue(key).build());
    return Optional.ofNullable(DdbMapper.toApi(item));
  }

  @Override
  public List<org.okapi.data.model.DashboardVersion> list(String orgId, String dashboardId) {
    var prefix = dashboardId + "#";
    var query =
        table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.sortBeginsWith(
                        Key.builder().partitionValue(orgId).sortValue(prefix).build()))
                .build());
    return Lists.newArrayList(new FlatteningIterator<>(query.iterator())).stream()
        .map(DdbMapper::toApi)
        .toList();
  }
}
