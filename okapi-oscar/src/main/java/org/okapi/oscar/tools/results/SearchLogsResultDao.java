/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import org.okapi.rest.logs.ChLogsQueryResponse;

public interface SearchLogsResultDao {
  PagedResultWrite persist(String toolCallId, ChLogsQueryResponse response);

  SearchLogsResultDetail getPage(String toolCallId, int pageNumber);
}
