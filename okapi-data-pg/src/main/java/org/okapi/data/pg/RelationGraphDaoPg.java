/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class RelationGraphDaoPg implements RelationGraphDao {
  private final JdbcTemplate jdbc;

  public RelationGraphDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private List<RelationGraphNode> all(EntityId value) {
    return jdbc.query(
        """
        SELECT target_type, target_id, array_agg(relation_type ORDER BY relation_type) relations
        FROM entity_relations
        WHERE source_type = ? AND source_id = ?
        GROUP BY target_type, target_id
        ORDER BY target_type, target_id
        """,
        (rs, row) -> node(value, rs),
        value.type().name(),
        value.id());
  }

  public Optional<RelationGraphNode> getRelationsBetween(EntityId left, EntityId right) {
    return jdbc
        .query(
            """
            SELECT target_type, target_id, array_agg(relation_type ORDER BY relation_type) relations
            FROM entity_relations
            WHERE source_type = ? AND source_id = ? AND target_type = ? AND target_id = ?
            GROUP BY target_type, target_id
            """,
            (rs, row) -> node(left, rs),
            left.type().name(),
            left.id(),
            right.type().name(),
            right.id())
        .stream()
        .findFirst();
  }

  public boolean hasRelationBetween(EntityId left, EntityId right, RelationType type) {
    return getRelationsBetween(left, right)
        .map(node -> node.getRelationships().contains(type))
        .orElse(false);
  }

  public void removeAllRelations(EntityId left, EntityId right) {
    jdbc.update(
        """
        DELETE FROM entity_relations
        WHERE (source_type = ? AND source_id = ? AND target_type = ? AND target_id = ?)
           OR (source_type = ? AND source_id = ? AND target_type = ? AND target_id = ?)
        """,
        left.type().name(),
        left.id(),
        right.type().name(),
        right.id(),
        right.type().name(),
        right.id(),
        left.type().name(),
        left.id());
  }

  public void removeRelation(EntityId left, EntityId right, RelationType type) {
    jdbc.update(
        """
        DELETE FROM entity_relations
        WHERE source_type = ? AND source_id = ? AND target_type = ? AND target_id = ?
          AND relation_type = ?
        """,
        left.type().name(),
        left.id(),
        right.type().name(),
        right.id(),
        type.name());
  }

  public RelationGraphNode addRelationship(EntityId left, EntityId right, RelationType type) {
    return addAllRelationships(left, right, List.of(type));
  }

  public RelationGraphNode addAllRelationships(
      EntityId left, EntityId right, List<RelationType> types) {
    for (var type : types) {
      jdbc.update(
          """
          INSERT INTO entity_relations
            (source_type, source_id, target_type, target_id, relation_type)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT DO NOTHING
          """,
          left.type().name(),
          left.id(),
          right.type().name(),
          right.id(),
          type.name());
    }
    return getRelationsBetween(left, right).orElseThrow();
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

  public List<RelationGraphNode> getAllIncomingRelations(
      EntityId entity, EntityType sourceType, RelationType relationType) {
    return jdbc.query(
        """
        SELECT source_type, source_id
        FROM entity_relations
        WHERE target_type = ? AND target_id = ? AND source_type = ? AND relation_type = ?
        ORDER BY source_id
        """,
        (rs, row) ->
            RelationGraphNode.builder()
                .entityId(entity.toString())
                .relatedEntity(EntityId.of(sourceType, rs.getString("source_id")).toString())
                .relatedEntityType(sourceType)
                .relationships(new java.util.ArrayList<>(List.of(relationType)))
                .build(),
        entity.type().name(),
        entity.id(),
        sourceType.name(),
        relationType.name());
  }

  public void deleteEntity(EntityId entity) {
    jdbc.update(
        """
        DELETE FROM entity_relations
        WHERE (source_type = ? AND source_id = ?) OR (target_type = ? AND target_id = ?)
        """,
        entity.type().name(),
        entity.id(),
        entity.type().name(),
        entity.id());
  }

  private RelationGraphNode node(EntityId source, java.sql.ResultSet rs)
      throws java.sql.SQLException {
    var targetType = EntityType.valueOf(rs.getString("target_type"));
    var relationships =
        java.util.Arrays.stream((String[]) rs.getArray("relations").getArray())
            .map(RelationType::valueOf)
            .toList();
    return RelationGraphNode.builder()
        .entityId(source.toString())
        .relatedEntity(EntityId.of(targetType, rs.getString("target_id")).toString())
        .relatedEntityType(targetType)
        .relationships(new java.util.ArrayList<>(relationships))
        .build();
  }

  private record PathNode(int pathIndex, EntityId node) {}
}
