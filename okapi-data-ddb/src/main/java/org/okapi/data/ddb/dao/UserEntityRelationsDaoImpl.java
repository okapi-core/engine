/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.ddb.attributes.serialization.EntityRelationIdConverter;
import org.okapi.data.dto.TablesAndIndexes;
import org.okapi.data.dto.UserEntityRelations;
import org.okapi.data.model.EntityRelationId;
import org.okapi.data.model.UserEntityRelation;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

public class UserEntityRelationsDaoImpl implements UserEntityRelationsDao {

  DynamoDbTable<UserEntityRelations> table;
  EntityRelationIdConverter converter = new EntityRelationIdConverter();

  @Inject
  public UserEntityRelationsDaoImpl(DynamoDbEnhancedClient enhancedClient) {
    this.table =
        enhancedClient.table(
            TablesAndIndexes.USER_ENTITY_RELATIONS_TABLE,
            TableSchema.fromBean(UserEntityRelations.class));
  }

  @Override
  public Optional<UserEntityRelation> getRelation(String userId, EntityRelationId edgeId) {
    var convertedEdgeId =
        converter.transformFrom(
            new org.okapi.data.ddb.attributes.EntityRelationId(
                org.okapi.data.ddb.attributes.ENTITY_TYPE.valueOf(edgeId.entityType().name()),
                edgeId.entityId(),
                org.okapi.data.ddb.attributes.USER_RELATION_TYPE.valueOf(
                    edgeId.relationType().name())));
    var query = table.getItem(r -> r.key(k -> k.partitionValue(userId).sortValue(convertedEdgeId)));
    return Optional.ofNullable(DdbMapper.toApi(query));
  }

  @Override
  public List<UserEntityRelation> listUserRelations(String userId) {
    var query =
        table.query(
            r ->
                r.queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build())));
    return query.items().stream().map(DdbMapper::toApi).toList();
  }

  @Override
  public void createRelation(UserEntityRelation userEntityRelation) {
    table.putItem(DdbMapper.toDdb(userEntityRelation));
  }

  @Override
  public void deleteRelation(String userId, EntityRelationId edgeId) {
    var convertedEdgeId =
        converter.transformFrom(
            new org.okapi.data.ddb.attributes.EntityRelationId(
                org.okapi.data.ddb.attributes.ENTITY_TYPE.valueOf(edgeId.entityType().name()),
                edgeId.entityId(),
                org.okapi.data.ddb.attributes.USER_RELATION_TYPE.valueOf(
                    edgeId.relationType().name())));
    table.deleteItem(r -> r.key(k -> k.partitionValue(userId).sortValue(convertedEdgeId)));
  }
}
