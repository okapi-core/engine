/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.model.EdgeAttributes;
import org.okapi.data.model.EntityRelationId;
import org.okapi.data.model.UserEntityRelation;
import org.okapi.data.pg.entity.UserEntityRelationEntity;
import org.okapi.data.pg.repository.UserEntityRelationRepository;

public final class UserEntityRelationsDaoPg implements UserEntityRelationsDao {
  private final UserEntityRelationRepository repository;

  public UserEntityRelationsDaoPg(UserEntityRelationRepository repository) {
    this.repository = repository;
  }

  public Optional<UserEntityRelation> getRelation(String user, EntityRelationId edge) {
    return repository.findById(id(user, edge)).map(this::toDto);
  }

  public List<UserEntityRelation> listUserRelations(String user) {
    return repository
        .findAllByIdUserIdOrderByIdEntityTypeAscIdEntityIdAscIdRelationTypeAsc(user)
        .stream()
        .map(this::toDto)
        .toList();
  }

  public void createRelation(UserEntityRelation relation) {
    var attributes = relation.getEdgeAttributes();
    repository.saveAndFlush(
        new UserEntityRelationEntity(
            id(relation.getUserId(), relation.getEdgeId()),
            attributes == null ? null : attributes.getTimestamp(),
            attributes == null ? null : attributes.getStringValue(),
            attributes == null ? null : attributes.isBooleanValue()));
  }

  public void deleteRelation(String user, EntityRelationId edge) {
    repository.deleteById(id(user, edge));
  }

  private UserEntityRelationEntity.Id id(String user, EntityRelationId edge) {
    return new UserEntityRelationEntity.Id(
        user, edge.entityType(), edge.entityId(), edge.relationType());
  }

  private UserEntityRelation toDto(UserEntityRelationEntity entity) {
    var id = entity.getId();
    var hasAttributes =
        entity.getEdgeTimestamp() != null
            || entity.getEdgeStringValue() != null
            || entity.getEdgeBooleanValue() != null;
    return UserEntityRelation.builder()
        .userId(id.getUserId())
        .edgeId(new EntityRelationId(id.getEntityType(), id.getEntityId(), id.getRelationType()))
        .edgeAttributes(
            hasAttributes
                ? EdgeAttributes.builder()
                    .timestamp(entity.getEdgeTimestamp() == null ? 0 : entity.getEdgeTimestamp())
                    .stringValue(entity.getEdgeStringValue())
                    .booleanValue(Boolean.TRUE.equals(entity.getEdgeBooleanValue()))
                    .build()
                : null)
        .build();
  }
}
