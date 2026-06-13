/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.FederatedSourceRepo;
import org.okapi.data.ddb.CommonQueryPatterns;
import org.okapi.data.dto.FederatedSource;
import org.okapi.data.dto.TablesAndIndexes;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class FederatedSourceRepoImpl implements FederatedSourceRepo {
  private final DynamoDbEnhancedClient enhanced;
  DynamoDbTable<FederatedSource> dynamoDbTable;
  CommonQueryPatterns<FederatedSource> commonQueryPatterns;

  @Inject
  public FederatedSourceRepoImpl(
      DynamoDbEnhancedClient enhanced, CommonQueryPatterns<FederatedSource> commonQueryPatterns) {
    this.enhanced = enhanced;
    this.dynamoDbTable =
        this.enhanced.table(
            TablesAndIndexes.FEDERATED_SOURCES_TABLE, TableSchema.fromBean(FederatedSource.class));
    this.commonQueryPatterns = commonQueryPatterns;
  }

  @Override
  public Optional<org.okapi.data.model.FederatedSource> getSource(
      String tenantId, String sourceName) {
    return commonQueryPatterns
        .getByCompositeKey(
            enhanced,
            dynamoDbTable,
            AttributeValue.builder().s(tenantId).build(),
            AttributeValue.builder().s(sourceName).build())
        .map(DdbMapper::toApi);
  }

  @Override
  public List<org.okapi.data.model.FederatedSource> getAllSources(String tenantId) {
    return commonQueryPatterns
        .listByPartitionKey(
            enhanced, dynamoDbTable, AttributeValue.builder().s(tenantId).build())
        .stream()
        .map(DdbMapper::toApi)
        .toList();
  }

  @Override
  public void createSource(org.okapi.data.model.FederatedSource source) {
    dynamoDbTable.putItem(DdbMapper.toDdb(source));
  }

  @Override
  public void deleteSource(String tenantId, String sourceName) {
    commonQueryPatterns.deleteByCompositeKey(
        enhanced,
        dynamoDbTable,
        AttributeValue.builder().s(tenantId).build(),
        AttributeValue.builder().s(sourceName).build());
  }
}
