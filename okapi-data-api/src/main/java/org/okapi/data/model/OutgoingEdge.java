/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

public record OutgoingEdge(EntityType outgoingNodeType, RelationType relationType) {
  public static OutgoingEdge of(EntityType type, RelationType relationType) {
    return new OutgoingEdge(type, relationType);
  }
}
