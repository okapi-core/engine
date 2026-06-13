/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import org.okapi.data.dao.TokenMetaDao;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.dto.TOKEN_STATUS;
import org.okapi.data.dto.TablesAndIndexes;
import org.okapi.data.dto.TokenMetaDdb;
import org.okapi.data.model.TokenMetadata;
import org.okapi.data.model.TokenStatus;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class TokenMetaDaoDdbImpl implements TokenMetaDao {

  private final DynamoDbEnhancedClient dynamoDbEnhancedClient;
  private final DynamoDbTable<TokenMetaDdb> tokenMetaTable;

  @Inject
  public TokenMetaDaoDdbImpl(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
    this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
    this.tokenMetaTable =
        this.dynamoDbEnhancedClient.table(
            TablesAndIndexes.TOKEN_META_TABLE, TableSchema.fromBean(TokenMetaDdb.class));
  }

  @Override
  public void createTokenMetadata(TokenMetadata tokenMeta) {
    tokenMetaTable.putItem(DdbMapper.toDdb(tokenMeta));
  }

  @Override
  public TokenMetadata getTokenMetadata(String orgId, String tokenId) {
    return DdbMapper.toApi(
        tokenMetaTable.getItem(Key.builder().partitionValue(orgId).sortValue(tokenId).build()));
  }

  @Override
  public void updateTokenStatus(String orgId, String tokenId, TokenStatus status) {
    var token = getTokenMetadata(orgId, tokenId);
    if (token == null) {
      return;
    }
    token.setTokenStatus(status);
    tokenMetaTable.updateItem(DdbMapper.toDdb(token));
  }

  @Override
  public List<TokenMetadata> listTokensByOrgAndStatus(String orgId, TokenStatus status) {
    var index = tokenMetaTable.index(TablesAndIndexes.TOKEN_META_BY_ORG_STATUS_GSI);
    var results =
        index.query(
            QueryConditional.keyEqualTo(
                Key.builder().partitionValue(orgId).sortValue(status.name()).build()));
    return Lists.newArrayList(new FlatteningIterator<>(results.iterator())).stream()
        .map(DdbMapper::toApi)
        .toList();
  }
}
