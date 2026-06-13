/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.exceptions.EntityDoesNotExistException;
import org.okapi.data.model.DependencyType;
import org.okapi.data.model.InfraEntityId;
import org.okapi.data.model.InfraEntityNode;
import org.okapi.data.model.InfraNodeOutgoingEdge;

public interface InfraEntityNodeDao {
  void createNode(InfraEntityNode node);

  <T> void updateNodeAttributes(InfraEntityId infraEntityId, T attributes, Class<T> clazz);

  void deleteNode(InfraEntityId infraEntityId);

  void addOutgoingEdge(InfraEntityId entityId, InfraNodeOutgoingEdge outgoingEdge)
      throws EntityDoesNotExistException;

  void removeEdge(InfraEntityId entityId, InfraEntityId targetNodeId);

  List<InfraNodeOutgoingEdge> getEdgesByType(InfraEntityId entityId, DependencyType depType);

  List<InfraNodeOutgoingEdge> getAllOutgoingEdges(InfraEntityId entityId);

  Optional<InfraEntityNode> getNode(InfraEntityId infraEntityId);
}
