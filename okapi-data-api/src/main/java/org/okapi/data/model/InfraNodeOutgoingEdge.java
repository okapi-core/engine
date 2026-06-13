/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

public record InfraNodeOutgoingEdge(
    InfraEntityId targetNodeId, String edgeAttributes, DependencyType depType) {
  public InfraEntityId getTargetNodeId() {
    return targetNodeId;
  }

  public String getEdgeAttributes() {
    return edgeAttributes;
  }

  public DependencyType getDepType() {
    return depType;
  }
}
