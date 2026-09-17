/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.InfraEntityNodeDao;
import org.okapi.data.exceptions.EntityDoesNotExistException;
import org.okapi.data.model.*;
import org.springframework.jdbc.core.JdbcTemplate;

public final class InfraEntityNodeDaoPg implements InfraEntityNodeDao {
  private final JdbcTemplate jdbc;
  private final Gson gson;

  public InfraEntityNodeDaoPg(JdbcTemplate jdbc, Gson gson) {
    this.jdbc = jdbc;
    this.gson = gson;
  }

  public void createNode(InfraEntityNode node) {
    if (node == null) return;
    var id = node.getInfraEntityId();
    jdbc.update(
        """
        INSERT INTO infra_entity_nodes (tenant_id, entity_type, entity_id, attributes)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (tenant_id, entity_type, entity_id) DO UPDATE SET
          attributes = EXCLUDED.attributes
        """,
        id.tenantId(),
        id.entityType().name(),
        id.id(),
        node.getAttributes());
    jdbc.update(
        "DELETE FROM infra_entity_edges WHERE source_tenant_id = ? AND source_entity_type = ? AND source_entity_id = ?",
        id.tenantId(),
        id.entityType().name(),
        id.id());
    if (node.getOutgoingEdges() != null) {
      node.getOutgoingEdges().forEach(edge -> insertEdge(id, edge));
    }
  }

  public <T> void updateNodeAttributes(InfraEntityId id, T attributes, Class<T> clazz) {
    jdbc.update(
        """
        INSERT INTO infra_entity_nodes (tenant_id, entity_type, entity_id, attributes)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (tenant_id, entity_type, entity_id) DO UPDATE SET
          attributes = EXCLUDED.attributes
        """,
        id.tenantId(),
        id.entityType().name(),
        id.id(),
        gson.toJson(attributes));
  }

  public void deleteNode(InfraEntityId id) {
    jdbc.update(
        "DELETE FROM infra_entity_nodes WHERE tenant_id = ? AND entity_type = ? AND entity_id = ?",
        id.tenantId(),
        id.entityType().name(),
        id.id());
  }

  public void addOutgoingEdge(InfraEntityId id, InfraNodeOutgoingEdge edge)
      throws EntityDoesNotExistException {
    if (getNode(id).isEmpty()) {
      throw new EntityDoesNotExistException("Cannot add edge FROM non-existent node");
    }
    if (getNode(edge.targetNodeId()).isEmpty()) {
      throw new EntityDoesNotExistException("Cannot create edge TO non-existent node");
    }
    insertEdge(id, edge);
  }

  public void removeEdge(InfraEntityId id, InfraEntityId target) {
    jdbc.update(
        """
        DELETE FROM infra_entity_edges
        WHERE source_tenant_id = ? AND source_entity_type = ? AND source_entity_id = ?
          AND target_tenant_id = ? AND target_entity_type = ? AND target_entity_id = ?
        """,
        id.tenantId(),
        id.entityType().name(),
        id.id(),
        target.tenantId(),
        target.entityType().name(),
        target.id());
  }

  public List<InfraNodeOutgoingEdge> getEdgesByType(InfraEntityId id, DependencyType type) {
    return getAllOutgoingEdges(id).stream().filter(edge -> edge.depType() == type).toList();
  }

  public List<InfraNodeOutgoingEdge> getAllOutgoingEdges(InfraEntityId id) {
    return jdbc.query(
        """
        SELECT * FROM infra_entity_edges
        WHERE source_tenant_id = ? AND source_entity_type = ? AND source_entity_id = ?
        ORDER BY target_tenant_id, target_entity_type, target_entity_id, dependency_type
        """,
        (rs, row) ->
            new InfraNodeOutgoingEdge(
                new InfraEntityId(
                    rs.getString("target_tenant_id"),
                    InfraEntityType.valueOf(rs.getString("target_entity_type")),
                    rs.getString("target_entity_id")),
                rs.getString("edge_attributes"),
                DependencyType.valueOf(rs.getString("dependency_type"))),
        id.tenantId(),
        id.entityType().name(),
        id.id());
  }

  public Optional<InfraEntityNode> getNode(InfraEntityId id) {
    return jdbc
        .query(
            """
            SELECT * FROM infra_entity_nodes
            WHERE tenant_id = ? AND entity_type = ? AND entity_id = ?
            """,
            (rs, row) ->
                new InfraEntityNode(
                    id, rs.getString("attributes"), new ArrayList<>(getAllOutgoingEdges(id))),
            id.tenantId(),
            id.entityType().name(),
            id.id())
        .stream()
        .findFirst();
  }

  private void insertEdge(InfraEntityId source, InfraNodeOutgoingEdge edge) {
    var target = edge.targetNodeId();
    jdbc.update(
        """
        INSERT INTO infra_entity_edges (
          source_tenant_id, source_entity_type, source_entity_id,
          target_tenant_id, target_entity_type, target_entity_id,
          dependency_type, edge_attributes
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (
          source_tenant_id, source_entity_type, source_entity_id,
          target_tenant_id, target_entity_type, target_entity_id, dependency_type
        ) DO UPDATE SET edge_attributes = EXCLUDED.edge_attributes
        """,
        source.tenantId(),
        source.entityType().name(),
        source.id(),
        target.tenantId(),
        target.entityType().name(),
        target.id(),
        edge.depType().name(),
        edge.edgeAttributes());
  }
}
