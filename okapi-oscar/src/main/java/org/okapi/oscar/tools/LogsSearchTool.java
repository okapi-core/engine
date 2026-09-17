/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.tools.results.LogsResultSummarizer;
import org.okapi.oscar.tools.results.SearchLogsResultDao;
import org.okapi.oscar.tools.results.SearchLogsResultSummary;
import org.okapi.rest.logs.ChLogsQueryRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class LogsSearchTool {
  private final IngesterClient client;
  private final ToolCallReporter toolCallReporter;
  private final SearchLogsResultDao searchLogsResultDao;
  private final LogsResultSummarizer logsResultSummarizer;

  public LogsSearchTool(
      IngesterClient client,
      ToolCallReporter toolCallReporter,
      SearchLogsResultDao searchLogsResultDao,
      LogsResultSummarizer logsResultSummarizer) {
    this.client = client;
    this.toolCallReporter = toolCallReporter;
    this.searchLogsResultDao = searchLogsResultDao;
    this.logsResultSummarizer = logsResultSummarizer;
  }

  @Tool(
      description =
"""
Search logs within a time window. tsStartNanos and tsEndNanos MUST be in NANOSECONDS since Unix epoch.
Multiple filters use AND semantics. String filters support exact or RE2 regular-expression matching.
Log-level filters use OpenTelemetry severity numbers. Prefer narrow time windows and conservative limits.
Returns a summary only. Use getLogDetails with the returned toolCallId to inspect one page of
matching log lines.
""")
  public SearchLogsResultSummary searchLogs(@ToolParam ChLogsQueryRequest request) {
    toolCallReporter.reportRequest(
        "searchLogs", request, ToolCallSummaries.summarizeLogSearchRequest(request));
    var response = client.searchLogs(request);
    String toolCallId = UUID.randomUUID().toString();
    var write = searchLogsResultDao.persist(toolCallId, response);
    var summary = logsResultSummarizer.getSummary(write);
    toolCallReporter.reportResponseSummaryOnly("searchLogs", formatSummaryForUi(summary));
    return summary;
  }

  private static String formatSummaryForUi(SearchLogsResultSummary summary) {
    return "Search logs results: logs="
        + summary.totalLines()
        + " pages="
        + summary.totalPages()
        + " toolCallId="
        + summary.toolCallId();
  }
}
