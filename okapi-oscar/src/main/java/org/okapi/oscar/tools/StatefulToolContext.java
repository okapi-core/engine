/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools;

import lombok.Getter;

@Getter
public class StatefulToolContext {
  private final StatefulTools statefulTools;
  private final MetricsTools metricsTools;
  private final TracingTools tracingTools;
  private final LogsSearchTool logsSearchTool;
  private final LogDetailsTool logDetailsTool;

  public StatefulToolContext(
      StatefulTools statefulTools,
      MetricsTools metricsTools,
      TracingTools tracingTools,
      LogsSearchTool logsSearchTool,
      LogDetailsTool logDetailsTool) {
    this.statefulTools = statefulTools;
    this.metricsTools = metricsTools;
    this.tracingTools = tracingTools;
    this.logsSearchTool = logsSearchTool;
    this.logDetailsTool = logDetailsTool;
  }
}
