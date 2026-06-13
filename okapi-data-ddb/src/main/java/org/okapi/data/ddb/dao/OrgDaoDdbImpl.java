/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import static org.okapi.data.dto.TablesAndIndexes.ORGS_TABLE;

import com.google.inject.Inject;
import java.util.Optional;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dto.OrgDtoDdb;
import org.okapi.data.model.Organization;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;

public class OrgDaoDdbImpl implements OrgDao {
  private final DynamoDbEnhancedClient enhanced;
  DynamoDbTable<OrgDtoDdb> dynamoDbTable;

  @Inject
  public OrgDaoDdbImpl(DynamoDbEnhancedClient enhanced) {
    this.enhanced = enhanced;
    dynamoDbTable = this.enhanced.table(ORGS_TABLE, TableSchema.fromBean(OrgDtoDdb.class));
  }

  @Override
  public Optional<Organization> findById(String orgId) {
    var obj =
        dynamoDbTable.getItem(
            GetItemEnhancedRequest.builder()
                .key(Key.builder().partitionValue(orgId).build())
                .build());
    return Optional.ofNullable(DdbMapper.toApi(obj));
  }

  @Override
  public void save(Organization organization) {
    var obj = DdbMapper.toDdb(organization);
    dynamoDbTable.putItem(obj);
  }
}
