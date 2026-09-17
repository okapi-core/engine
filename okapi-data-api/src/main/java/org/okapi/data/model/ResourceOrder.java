/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ResourceOrder {
  private List<String> resourceIds = new ArrayList<>();

  public ResourceOrder() {}

  public ResourceOrder(List<String> resourceIds) {
    this.resourceIds = new ArrayList<>(resourceIds);
  }

  public List<String> asList() {
    return resourceIds;
  }

  public ResourceOrder add(String resourceId) {
    resourceIds.add(resourceId);
    return this;
  }

  public int size() {
    return resourceIds.size();
  }

  public static ResourceOrder from(List<String> ids) {
    return new ResourceOrder(ids);
  }

  public static ResourceOrder from(String... ids) {
    return new ResourceOrder(Arrays.asList(ids));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ResourceOrder that && Objects.equals(resourceIds, that.resourceIds);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceIds);
  }
}
