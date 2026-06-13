/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.InfraEntityNodeDao;
import org.okapi.data.ddb.attributes.InfraNodeOutgoingEdges;
import org.okapi.data.ddb.attributes.serialization.InfraEntityIdDdbConverter;
import org.okapi.data.dto.GsonSingleton;
import org.okapi.data.dto.InfraEntityNodeDdb;
import org.okapi.data.dto.TablesAndIndexes;
import org.okapi.data.exceptions.EntityDoesNotExistException;
import org.okapi.data.model.DependencyType;
import org.okapi.data.model.InfraEntityId;
import org.okapi.data.model.InfraEntityNode;
import org.okapi.data.model.InfraNodeOutgoingEdge;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

public class InfraEntityNodeDaoDdbImpl implements InfraEntityNodeDao {
  private final DynamoDbTable<InfraEntityNodeDdb> table;
  private final InfraEntityIdDdbConverter keyConverter = new InfraEntityIdDdbConverter();

  @Inject
  public InfraEntityNodeDaoDdbImpl(DynamoDbEnhancedClient client) {
    table =
        client.table(
            TablesAndIndexes.INFRA_ENTITY_NODES_TABLE,
            TableSchema.fromBean(InfraEntityNodeDdb.class));
  }

  @Override
  public void createNode(InfraEntityNode node) {
    if (node != null) table.putItem(toDdb(node));
  }

  @Override
  public <T> void updateNodeAttributes(InfraEntityId id, T attributes, Class<T> clazz) {
    var node = getNode(id).orElseGet(() -> new InfraEntityNode(id, null, new ArrayList<>()));
    node.setAttributes(GsonSingleton.SINGLETON.toJson(attributes));
    table.putItem(toDdb(node));
  }

  @Override
  public void deleteNode(InfraEntityId id) {
    table.deleteItem(
        Key.builder().partitionValue(keyConverter.transformFrom(toDdb(id))).build());
  }

  @Override
  public void addOutgoingEdge(InfraEntityId id, InfraNodeOutgoingEdge edge)
      throws EntityDoesNotExistException {
    var node =
        getNode(id)
            .orElseThrow(
                () -> new EntityDoesNotExistException("Cannot add edge FROM non-existent node"));
    if (getNode(edge.targetNodeId()).isEmpty()) {
      throw new EntityDoesNotExistException("Cannot create edge TO non-existent node");
    }
    var edges = new ArrayList<>(node.getOutgoingEdges());
    edges.add(edge);
    node.setOutgoingEdges(edges);
    table.putItem(toDdb(node));
  }

  @Override
  public void removeEdge(InfraEntityId id, InfraEntityId targetId) {
    getNode(id)
        .ifPresent(
            node -> {
              var edges = new ArrayList<>(node.getOutgoingEdges());
              edges.removeIf(edge -> edge.targetNodeId().equals(targetId));
              node.setOutgoingEdges(edges);
              table.putItem(toDdb(node));
            });
  }

  @Override
  public List<InfraNodeOutgoingEdge> getEdgesByType(InfraEntityId id, DependencyType type) {
    return getAllOutgoingEdges(id).stream().filter(edge -> edge.depType() == type).toList();
  }

  @Override
  public List<InfraNodeOutgoingEdge> getAllOutgoingEdges(InfraEntityId id) {
    return getNode(id)
        .map(InfraEntityNode::getOutgoingEdges)
        .map(ArrayList::new)
        .orElseGet(ArrayList::new);
  }

  @Override
  public Optional<InfraEntityNode> getNode(InfraEntityId id) {
    return Optional.ofNullable(
            table.getItem(
                Key.builder().partitionValue(keyConverter.transformFrom(toDdb(id))).build()))
        .map(this::toApi);
  }

  private org.okapi.data.ddb.attributes.InfraEntityId toDdb(InfraEntityId id) {
    return new org.okapi.data.ddb.attributes.InfraEntityId(
        id.tenantId(),
        org.okapi.data.ddb.attributes.INFRA_ENTITY_TYPE.valueOf(id.entityType().name()),
        id.id());
  }

  private InfraEntityId toApi(org.okapi.data.ddb.attributes.InfraEntityId id) {
    return new InfraEntityId(
        id.getTenantId(),
        org.okapi.data.model.InfraEntityType.valueOf(id.getEntityType().name()),
        id.getId());
  }

  private org.okapi.data.ddb.attributes.InfraNodeOutgoingEdge toDdb(InfraNodeOutgoingEdge edge) {
    return new org.okapi.data.ddb.attributes.InfraNodeOutgoingEdge(
        toDdb(edge.targetNodeId()),
        edge.edgeAttributes(),
        org.okapi.data.ddb.attributes.DEP_TYPE.valueOf(edge.depType().name()));
  }

  private InfraNodeOutgoingEdge toApi(
      org.okapi.data.ddb.attributes.InfraNodeOutgoingEdge edge) {
    return new InfraNodeOutgoingEdge(
        toApi(edge.getTargetNodeId()),
        edge.getEdgeAttributes(),
        DependencyType.valueOf(edge.getDepType().name()));
  }

  private InfraEntityNodeDdb toDdb(InfraEntityNode node) {
    var edges =
        node.getOutgoingEdges() == null
            ? Collections.<org.okapi.data.ddb.attributes.InfraNodeOutgoingEdge>emptyList()
            : node.getOutgoingEdges().stream().map(this::toDdb).toList();
    return new InfraEntityNodeDdb(
        toDdb(node.getInfraEntityId()), node.getAttributes(), new InfraNodeOutgoingEdges(edges));
  }

  private InfraEntityNode toApi(InfraEntityNodeDdb node) {
    var edges =
        node.getOutgoingEdges() == null || node.getOutgoingEdges().getEdges() == null
            ? Collections.<InfraNodeOutgoingEdge>emptyList()
            : node.getOutgoingEdges().getEdges().stream().map(this::toApi).toList();
    return new InfraEntityNode(toApi(node.getInfraEntityId()), node.getAttributes(), edges);
  }
}
