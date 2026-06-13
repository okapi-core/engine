/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.model.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class UserEntityRelationsDaoPg implements UserEntityRelationsDao {
  private final JdbcTemplate jdbc;

  public UserEntityRelationsDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<UserEntityRelation> getRelation(String user, EntityRelationId edge) {
    return jdbc
        .query(
            """
            SELECT * FROM user_entity_relations
            WHERE user_id = ? AND entity_type = ? AND entity_id = ? AND relation_type = ?
            """,
            this::map,
            user,
            edge.entityType().name(),
            edge.entityId(),
            edge.relationType().name())
        .stream()
        .findFirst();
  }

  public List<UserEntityRelation> listUserRelations(String user) {
    return jdbc.query(
        "SELECT * FROM user_entity_relations WHERE user_id = ? ORDER BY entity_type, entity_id, relation_type",
        this::map,
        user);
  }

  public void createRelation(UserEntityRelation relation) {
    var edge = relation.getEdgeId();
    var attributes = relation.getEdgeAttributes();
    jdbc.update(
        """
        INSERT INTO user_entity_relations (
          user_id, entity_type, entity_id, relation_type,
          edge_timestamp, edge_string_value, edge_boolean_value
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id, entity_type, entity_id, relation_type) DO UPDATE SET
          edge_timestamp = EXCLUDED.edge_timestamp,
          edge_string_value = EXCLUDED.edge_string_value,
          edge_boolean_value = EXCLUDED.edge_boolean_value
        """,
        relation.getUserId(),
        edge.entityType().name(),
        edge.entityId(),
        edge.relationType().name(),
        attributes == null ? null : attributes.getTimestamp(),
        attributes == null ? null : attributes.getStringValue(),
        attributes == null ? null : attributes.isBooleanValue());
  }

  public void deleteRelation(String user, EntityRelationId edge) {
    jdbc.update(
        """
        DELETE FROM user_entity_relations
        WHERE user_id = ? AND entity_type = ? AND entity_id = ? AND relation_type = ?
        """,
        user,
        edge.entityType().name(),
        edge.entityId(),
        edge.relationType().name());
  }

  private UserEntityRelation map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    var timestamp = rs.getLong("edge_timestamp");
    var hasAttributes = !rs.wasNull();
    var booleanValue = rs.getBoolean("edge_boolean_value");
    hasAttributes |= !rs.wasNull();
    var stringValue = rs.getString("edge_string_value");
    hasAttributes |= stringValue != null;
    return UserEntityRelation.builder()
        .userId(rs.getString("user_id"))
        .edgeId(
            new EntityRelationId(
                EntityType.valueOf(rs.getString("entity_type")),
                rs.getString("entity_id"),
                UserRelationType.valueOf(rs.getString("relation_type"))))
        .edgeAttributes(
            hasAttributes
                ? EdgeAttributes.builder()
                    .timestamp(timestamp)
                    .stringValue(stringValue)
                    .booleanValue(booleanValue)
                    .build()
                : null)
        .build();
  }
}
