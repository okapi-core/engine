/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import lombok.AllArgsConstructor;
import org.okapi.ingester.client.IngesterClient;
import org.okapi.oscar.chat.ChatMessageRepository;
import org.okapi.oscar.tools.results.LogsResultSummarizer;
import org.okapi.oscar.tools.results.SearchLogsResultDao;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class StatefulToolFactory {

  private final ChatMessageRepository repository;
  private final IngesterClient ingesterClient;
  private final SearchLogsResultDao searchLogsResultDao;
  private final LogsResultSummarizer logsResultSummarizer;

  public StatefulToolContext getTools(String sessionId, long streamId) {
    var statefulTools = StatefulTools.forSessionAndStream(sessionId, streamId, repository);
    var reporter = new ToolCallReporter(sessionId, streamId, statefulTools);
    var metricsTools = new MetricsTools(ingesterClient, reporter);
    var tracingTools = new TracingTools(ingesterClient, reporter);
    var logSearchTool =
        new LogsSearchTool(ingesterClient, reporter, searchLogsResultDao, logsResultSummarizer);
    var logDetailsTool = new LogDetailsTool(searchLogsResultDao);
    return new StatefulToolContext(
        statefulTools, metricsTools, tracingTools, logSearchTool, logDetailsTool);
  }
}
