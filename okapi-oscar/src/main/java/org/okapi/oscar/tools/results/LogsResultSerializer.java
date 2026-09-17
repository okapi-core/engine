/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

public interface LogsResultSerializer {
  byte[] serialize(SearchLogsResultDetail detail);

  SearchLogsResultDetail deserialize(byte[] bytes);
}
