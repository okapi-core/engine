/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import lombok.AllArgsConstructor;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.ddb.iterators.MappingIterator;
import org.okapi.data.dto.RelationGraphNodeDdb;
import org.okapi.data.dto.TablesAndIndexes;
import org.okapi.data.model.EdgeSequence;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.EntityType;
import org.okapi.data.model.RelationGraphNode;
import org.okapi.data.model.RelationType;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

public class RelationGraphDaoImpl implements RelationGraphDao {
  private final DynamoDbTable<RelationGraphNodeDdb> table;

  @Inject
  public RelationGraphDaoImpl(DynamoDbEnhancedClient client) {
    table =
        client.table(
            TablesAndIndexes.RELATIONSHIP_GRAPH_TABLE,
            TableSchema.fromBean(RelationGraphNodeDdb.class));
  }

  private String key(EntityId id) {
    return id.toString();
  }

  private Iterator<RelationGraphNode> getAllRelationsOf(EntityId id) {
    var results =
        table.query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(key(id)).build()))
                .build());
    return new MappingIterator<>(
        new FlatteningIterator<>(results.iterator()), this::toApi);
  }

  @Override
  public Optional<RelationGraphNode> getRelationsBetween(EntityId left, EntityId right) {
    return Optional.ofNullable(
            table.getItem(
                Key.builder().partitionValue(key(left)).sortValue(key(right)).build()))
        .map(this::toApi);
  }

  @Override
  public boolean hasRelationBetween(EntityId left, EntityId right, RelationType relationType) {
    return getRelationsBetween(left, right)
        .map(node -> node.getRelationships().contains(relationType))
        .orElse(false);
  }

  @Override
  public void removeAllRelations(EntityId left, EntityId right) {
    table.deleteItem(Key.builder().partitionValue(key(left)).sortValue(key(right)).build());
    table.deleteItem(Key.builder().partitionValue(key(right)).sortValue(key(left)).build());
  }

  @Override
  public void removeRelation(EntityId left, EntityId right, RelationType relationType) {
    getRelationsBetween(left, right)
        .ifPresent(
            node -> {
              node.getRelationships().remove(relationType);
              table.putItem(toDdb(node));
            });
  }

  @Override
  public RelationGraphNode addRelationship(
      EntityId left, EntityId right, RelationType relationType) {
    return addAllRelationships(left, right, List.of(relationType));
  }

  @Override
  public RelationGraphNode addAllRelationships(
      EntityId left, EntityId right, List<RelationType> relationTypes) {
    var node =
        getRelationsBetween(left, right)
            .orElseGet(
                () ->
                    RelationGraphNode.builder()
                        .entityId(key(left))
                        .relatedEntity(key(right))
                        .relatedEntityType(right.type())
                        .relationships(new ArrayList<>())
                        .build());
    relationTypes.stream()
        .filter(relation -> !node.getRelationships().contains(relation))
        .forEach(node.getRelationships()::add);
    node.setRelatedEntityType(right.type());
    table.putItem(toDdb(node));
    return node;
  }

  @AllArgsConstructor
  private static class PathNode {
    private int pathIndex;
    private EntityId node;
  }

  @Override
  public boolean isPathBetween(EntityId start, EntityId destination, EdgeSequence acceptedPath) {
    Queue<PathNode> nodes = new ArrayDeque<>();
    nodes.add(new PathNode(0, start));
    while (!nodes.isEmpty()) {
      var pathNode = nodes.poll();
      if (pathNode.pathIndex >= acceptedPath.accepted().size()) continue;
      var requiredEdge = acceptedPath.accepted().get(pathNode.pathIndex);
      if (pathNode.pathIndex == acceptedPath.accepted().size() - 1) {
        var relation = getRelationsBetween(pathNode.node, destination);
        if (relation.isPresent()
            && relation.get().getRelatedEntityType() == requiredEdge.outgoingNodeType()
            && relation.get().getRelationships().contains(requiredEdge.relationType())) {
          return true;
        }
        continue;
      }
      var relations = getAllRelationsOf(pathNode.node);
      while (relations.hasNext()) {
        var relation = relations.next();
        if (relation.getRelatedEntityType() == requiredEdge.outgoingNodeType()
            && relation.getRelationships().contains(requiredEdge.relationType())) {
          EntityId.parse(relation.getRelatedEntity())
              .ifPresent(next -> nodes.add(new PathNode(pathNode.pathIndex + 1, next)));
        }
      }
    }
    return false;
  }

  @Override
  public boolean isAnyPathBetween(
      EntityId start, EntityId destination, List<EdgeSequence> acceptedPaths) {
    return acceptedPaths.stream().anyMatch(path -> isPathBetween(start, destination, path));
  }

  @Override
  public List<RelationGraphNode> getAllRelationsOfType(
      EntityId entityId, EntityType entityType, RelationType relationType) {
    var matches = new ArrayList<RelationGraphNode>();
    getAllRelationsOf(entityId)
        .forEachRemaining(
            relation -> {
              if (relation.getRelatedEntityType() == entityType
                  && relation.getRelationships().contains(relationType)) {
                matches.add(relation);
              }
            });
    return matches;
  }

  @Override
  public List<RelationGraphNode> getAllRelationsOfNodeType(
      EntityId entityId, EntityType entityType) {
    var matches = new ArrayList<RelationGraphNode>();
    getAllRelationsOf(entityId)
        .forEachRemaining(
            relation -> {
              if (relation.getRelatedEntityType() == entityType) matches.add(relation);
            });
    return matches;
  }

  @Override
  public void deleteEntity(EntityId entityId) {
    var relations = getAllRelationsOf(entityId);
    while (relations.hasNext()) {
      var relation = relations.next();
      EntityId.parse(relation.getRelatedEntity())
          .ifPresent(
              related ->
                  table.deleteItem(
                      Key.builder()
                          .partitionValue(key(related))
                          .sortValue(key(entityId))
                          .build()));
      table.deleteItem(
          Key.builder()
              .partitionValue(key(entityId))
              .sortValue(relation.getRelatedEntity())
              .build());
    }
  }

  private RelationGraphNodeDdb toDdb(RelationGraphNode value) {
    return RelationGraphNodeDdb.builder()
        .entityId(value.getEntityId())
        .relatedEntity(value.getRelatedEntity())
        .relatedEntityType(
            org.okapi.data.ddb.attributes.ENTITY_TYPE.valueOf(
                value.getRelatedEntityType().name()))
        .relationships(
            value.getRelationships().stream()
                .map(r -> org.okapi.data.ddb.attributes.RELATION_TYPE.valueOf(r.name()))
                .toList())
        .build();
  }

  private RelationGraphNode toApi(RelationGraphNodeDdb value) {
    return RelationGraphNode.builder()
        .entityId(value.getEntityId())
        .relatedEntity(value.getRelatedEntity())
        .relatedEntityType(EntityType.valueOf(value.getRelatedEntityType().name()))
        .relationships(
            value.getRelationships().stream()
                .map(r -> RelationType.valueOf(r.name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
        .build();
  }
}
