/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import static org.okapi.data.pg.PgKeys.key;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.InfraEntityNodeDao;
import org.okapi.data.exceptions.EntityDoesNotExistException;
import org.okapi.data.model.*;

public final class InfraEntityNodeDaoPg implements InfraEntityNodeDao {
  private final JdbcRecordStore store;

  public InfraEntityNodeDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  private String entityKey(InfraEntityId id) {
    return key(id.tenantId(), id.entityType().name(), id.id());
  }

  public void createNode(InfraEntityNode node) {
    if (node != null) {
      store.put(
          "infra-entity",
          entityKey(node.getInfraEntityId()),
          node.getInfraEntityId().tenantId(),
          null,
          null,
          null,
          node);
    }
  }

  public <T> void updateNodeAttributes(InfraEntityId id, T attributes, Class<T> clazz) {
    var node = getNode(id).orElseGet(() -> new InfraEntityNode(id, null, new ArrayList<>()));
    node.setAttributes(store.gson().toJson(attributes));
    createNode(node);
  }

  public void deleteNode(InfraEntityId id) {
    store.delete("infra-entity", entityKey(id));
  }

  public void addOutgoingEdge(InfraEntityId id, InfraNodeOutgoingEdge edge)
      throws EntityDoesNotExistException {
    var node =
        getNode(id)
            .orElseThrow(
                () -> new EntityDoesNotExistException("Cannot add edge FROM non-existent node"));
    if (getNode(edge.targetNodeId()).isEmpty()) {
      throw new EntityDoesNotExistException("Cannot create edge TO non-existent node");
    }
    var edges =
        node.getOutgoingEdges() == null
            ? new ArrayList<InfraNodeOutgoingEdge>()
            : new ArrayList<>(node.getOutgoingEdges());
    edges.add(edge);
    node.setOutgoingEdges(edges);
    createNode(node);
  }

  public void removeEdge(InfraEntityId id, InfraEntityId target) {
    getNode(id)
        .ifPresent(
            node -> {
              var edges = new ArrayList<>(node.getOutgoingEdges());
              edges.removeIf(edge -> edge.targetNodeId().equals(target));
              node.setOutgoingEdges(edges);
              createNode(node);
            });
  }

  public List<InfraNodeOutgoingEdge> getEdgesByType(InfraEntityId id, DependencyType type) {
    return getAllOutgoingEdges(id).stream().filter(edge -> edge.depType() == type).toList();
  }

  public List<InfraNodeOutgoingEdge> getAllOutgoingEdges(InfraEntityId id) {
    return getNode(id)
        .map(InfraEntityNode::getOutgoingEdges)
        .map(ArrayList::new)
        .orElseGet(ArrayList::new);
  }

  public Optional<InfraEntityNode> getNode(InfraEntityId id) {
    return store.get("infra-entity", entityKey(id), InfraEntityNode.class);
  }
}
