/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

public record DataSourceQuery(String query, String sourceId) {
  public String getQuery() {
    return query;
  }

  public String getSourceId() {
    return sourceId;
  }
}
