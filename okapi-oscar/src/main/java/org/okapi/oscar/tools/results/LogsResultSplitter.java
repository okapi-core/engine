/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.List;
import org.okapi.rest.logs.ChLogsQueryResponse;

public interface LogsResultSplitter {
  List<SearchLogsResultDetail> split(
      String toolCallId, ChLogsQueryResponse response, int maxSizePerPage);
}
