/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.agents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.okapi.oscar.tools.*;

@Slf4j
public class CannedToolCallSequence {

  public interface Step {
    void run(Context context);
  }

  @Getter private final String name;
  private final List<Step> steps;
  private final long maxGapMillis;

  public CannedToolCallSequence(String name, List<Step> steps, long maxGapMillis) {
    this.name = name;
    this.steps = steps;
    this.maxGapMillis = maxGapMillis;
  }

  public void runBlocking(Context context) {
    long lastEnd = 0;
    for (var step : steps) {
      if (context.isAborted()) {
        return;
      }
      long now = System.currentTimeMillis();
      if (lastEnd != 0 && now - lastEnd > maxGapMillis) {
        log.warn("Sequence '{}' exceeded max gap: {}ms", name, now - lastEnd);
      }
      step.run(context);
      lastEnd = System.currentTimeMillis();
    }
  }

  public static class Context {
    private final String sessionId;
    private final long streamId;
    private final String userMessage;
    private final MetricsTools metricsTools;
    private final TracingTools tracingTools;
    private final LogsSearchTool logsSearchTool;
    private final DateTimeTools dateTimeTools;
    private final GreetingTools greetingTools;
    private final FilterContributionTool filterContributionTool;
    private final StatefulTools statefulTools;
    private final Map<String, Object> scratch;
    private boolean aborted;

    public Context(
        String sessionId,
        long streamId,
        String userMessage,
        MetricsTools metricsTools,
        TracingTools tracingTools,
        LogsSearchTool logsSearchTool,
        DateTimeTools dateTimeTools,
        GreetingTools greetingTools,
        FilterContributionTool filterContributionTool,
        StatefulTools statefulTools) {
      this.sessionId = sessionId;
      this.streamId = streamId;
      this.userMessage = userMessage;
      this.metricsTools = metricsTools;
      this.tracingTools = tracingTools;
      this.logsSearchTool = logsSearchTool;
      this.dateTimeTools = dateTimeTools;
      this.greetingTools = greetingTools;
      this.filterContributionTool = filterContributionTool;
      this.statefulTools = statefulTools;
      this.scratch = new HashMap<>();
    }

    public String getSessionId() {
      return sessionId;
    }

    public long getStreamId() {
      return streamId;
    }

    public String getUserMessage() {
      return userMessage;
    }

    public MetricsTools getMetricsTools() {
      return metricsTools;
    }

    public TracingTools getTracingTools() {
      return tracingTools;
    }

    public LogsSearchTool getLogSearchTool() {
      return logsSearchTool;
    }

    public DateTimeTools getDateTimeTools() {
      return dateTimeTools;
    }

    public GreetingTools getGreetingTools() {
      return greetingTools;
    }

    public FilterContributionTool getFilterContributionTool() {
      return filterContributionTool;
    }

    public StatefulTools getStatefulTools() {
      return statefulTools;
    }

    public void put(String key, Object value) {
      scratch.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
      var value = scratch.get(key);
      if (value == null) {
        return null;
      }
      return type.cast(value);
    }

    public void abort() {
      this.aborted = true;
    }

    public boolean isAborted() {
      return aborted;
    }
  }
}
