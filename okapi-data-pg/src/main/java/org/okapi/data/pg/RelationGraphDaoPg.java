/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.*;

public final class RelationGraphDaoPg implements RelationGraphDao {
  private final JdbcRecordStore store;

  public RelationGraphDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private List<RelationGraphNode> all(EntityId value) {
    return store.list("relation", value.toString(), null, RelationGraphNode.class);
  }

  public Optional<RelationGraphNode> getRelationsBetween(EntityId left, EntityId right) {
    return store.get("relation", key(left.toString(), right.toString()), RelationGraphNode.class);
  }

  public boolean hasRelationBetween(EntityId left, EntityId right, RelationType type) {
    return getRelationsBetween(left, right)
        .map(node -> node.getRelationships().contains(type))
        .orElse(false);
  }

  public void removeAllRelations(EntityId left, EntityId right) {
    store.delete("relation", key(left.toString(), right.toString()));
    store.delete("relation", key(right.toString(), left.toString()));
  }

  public void removeRelation(EntityId left, EntityId right, RelationType type) {
    getRelationsBetween(left, right)
        .ifPresent(
            node -> {
              node.getRelationships().remove(type);
              save(node);
            });
  }

  public RelationGraphNode addRelationship(EntityId left, EntityId right, RelationType type) {
    return addAllRelationships(left, right, List.of(type));
  }

  public RelationGraphNode addAllRelationships(
      EntityId left, EntityId right, List<RelationType> types) {
    var node =
        getRelationsBetween(left, right)
            .orElseGet(
                () ->
                    RelationGraphNode.builder()
                        .entityId(left.toString())
                        .relatedEntity(right.toString())
                        .relatedEntityType(right.type())
                        .relationships(new ArrayList<>())
                        .build());
    types.stream()
        .filter(type -> !node.getRelationships().contains(type))
        .forEach(node.getRelationships()::add);
    save(node);
    return node;
  }

  public boolean isPathBetween(EntityId start, EntityId end, EdgeSequence acceptedPath) {
    Queue<PathNode> nodes = new ArrayDeque<>();
    nodes.add(new PathNode(0, start));
    while (!nodes.isEmpty()) {
      var current = nodes.remove();
      if (current.pathIndex() >= acceptedPath.accepted().size()) continue;
      var expected = acceptedPath.accepted().get(current.pathIndex());
      for (var relation : all(current.node())) {
        if (relation.getRelatedEntityType() != expected.outgoingNodeType()
            || !relation.getRelationships().contains(expected.relationType())) continue;
        var next = EntityId.parse(relation.getRelatedEntity());
        if (next.isEmpty()) continue;
        if (current.pathIndex() == acceptedPath.accepted().size() - 1 && next.get().equals(end))
          return true;
        nodes.add(new PathNode(current.pathIndex() + 1, next.get()));
      }
    }
    return false;
  }

  public boolean isAnyPathBetween(EntityId start, EntityId end, List<EdgeSequence> paths) {
    return paths.stream().anyMatch(path -> isPathBetween(start, end, path));
  }

  public List<RelationGraphNode> getAllRelationsOfType(
      EntityId entity, EntityType type, RelationType relationType) {
    return all(entity).stream()
        .filter(
            relation ->
                relation.getRelatedEntityType() == type
                    && relation.getRelationships().contains(relationType))
        .toList();
  }

  public List<RelationGraphNode> getAllRelationsOfNodeType(EntityId entity, EntityType type) {
    return all(entity).stream()
        .filter(relation -> relation.getRelatedEntityType() == type)
        .toList();
  }

  public void deleteEntity(EntityId entity) {
    var id = entity.toString();
    for (var relation : all(entity)) {
      store.delete("relation", key(relation.getRelatedEntity(), id));
    }
    store.deleteByScope("relation", id);
    store.deleteBySource("relation", id);
  }

  private void save(RelationGraphNode node) {
    store.put(
        "relation",
        key(node.getEntityId(), node.getRelatedEntity()),
        node.getEntityId(),
        node.getRelatedEntityType().name(),
        null,
        node.getRelatedEntity(),
        node);
  }

  private record PathNode(int pathIndex, EntityId node) {}
}
