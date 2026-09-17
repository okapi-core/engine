/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

public record EntityRelationId(
    EntityType entityType, String entityId, UserRelationType relationType) {
  public EntityType getEntityType() {
    return entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public UserRelationType getRelationType() {
    return relationType;
  }
}
