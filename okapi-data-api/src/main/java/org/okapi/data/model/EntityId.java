/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.Optional;

public record EntityId(EntityType type, String id) {
  public static EntityId of(EntityType type, String id) {
    return new EntityId(type, id);
  }

  public static Optional<EntityId> parse(String value) {
    if (value == null) return Optional.empty();
    var separator = value.indexOf(':');
    if (separator < 1 || separator == value.length() - 1) return Optional.empty();
    try {
      return Optional.of(
          new EntityId(EntityType.valueOf(value.substring(0, separator)), value.substring(separator + 1)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  @Override
  public String toString() {
    return type.name() + ":" + id;
  }
}
