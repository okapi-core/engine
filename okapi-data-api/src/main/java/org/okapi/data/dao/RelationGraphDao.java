/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.model.EdgeSequence;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.EntityType;
import org.okapi.data.model.OutgoingEdge;
import org.okapi.data.model.RelationGraphNode;
import org.okapi.data.model.RelationType;

public interface RelationGraphDao {

  static OutgoingEdge makeRelation(EntityType entityType, RelationType relationType) {
    return new OutgoingEdge(entityType, relationType);
  }

  Optional<RelationGraphNode> getRelationsBetween(EntityId start, EntityId end);

  boolean hasRelationBetween(EntityId left, EntityId right, RelationType relationType);

  void removeAllRelations(EntityId left, EntityId right);

  void removeRelation(EntityId left, EntityId right, RelationType relationType);

  RelationGraphNode addRelationship(EntityId left, EntityId right, RelationType relationType);

  RelationGraphNode addAllRelationships(
      EntityId left, EntityId right, List<RelationType> relationType);

  boolean isPathBetween(EntityId start, EntityId end, EdgeSequence acceptedPath);

  boolean isAnyPathBetween(EntityId start, EntityId end, List<EdgeSequence> acceptedPaths);

  List<RelationGraphNode> getAllRelationsOfType(
      EntityId entityId, EntityType entityType, RelationType relationType);

  List<RelationGraphNode> getAllRelationsOfNodeType(EntityId entityId, EntityType type);

  void deleteEntity(EntityId entityId);
}
