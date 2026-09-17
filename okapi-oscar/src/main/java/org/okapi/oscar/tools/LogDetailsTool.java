/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import org.okapi.oscar.tools.results.SearchLogsResultDao;
import org.okapi.oscar.tools.results.SearchLogsResultDetail;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class LogDetailsTool {

  private final SearchLogsResultDao searchLogsResultDao;

  public LogDetailsTool(SearchLogsResultDao searchLogsResultDao) {
    this.searchLogsResultDao = searchLogsResultDao;
  }

  @Tool(
      description =
"""
Fetch one bounded page of log lines from a previous searchLogs call. Only call this after searchLogs
returns a SearchLogsResultSummary. Do not fetch every page by default; fetch page 1 first, then
consider later pages when useful. Log results are sorted, so a useful trick is to inspect pages out
of order; for example, if there are 5 pages, page 1 followed by page 3 may be enough before deciding
whether page 2 is worth fetching. pageNumber is one-based and must be between 1 and totalPages
returned by searchLogs.
""")
  public SearchLogsResultDetail getLogDetails(
      @ToolParam(description = "toolCallId returned by searchLogs.") String toolCallId,
      @ToolParam(description = "One-based page number to fetch.") int pageNumber) {
    return searchLogsResultDao.getPage(toolCallId, pageNumber);
  }
}
