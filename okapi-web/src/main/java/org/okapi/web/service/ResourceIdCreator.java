/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

public final class ResourceIdCreator {
  private ResourceIdCreator() {}

  public static String createResourceId(String... parts) {
    if (parts == null || parts.length == 0) {
      throw new IllegalArgumentException("At least one component is required");
    }
    for (int i = 0; i < parts.length; i++) {
      if (parts[i] == null || parts[i].isEmpty()) {
        throw new IllegalArgumentException("ResourceId part cannot be null/empty at index " + i);
      }
    }
    return String.join(":", parts);
  }
}
