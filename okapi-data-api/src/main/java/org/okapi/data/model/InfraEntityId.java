/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

public record InfraEntityId(String tenantId, InfraEntityType entityType, String id) {
  public String getTenantId() {
    return tenantId;
  }

  public InfraEntityType getEntityType() {
    return entityType;
  }

  public String getId() {
    return id;
  }
}
