/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.okapi.rest.metrics.ExportMetricsRequest;

public class RewritePostProcessor implements MetricsPostProcessor {
  private final RewritePipeline pipeline;

  public RewritePostProcessor(RewritePipeline pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public List<ExportMetricsRequest> process(List<ExportMetricsRequest> input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    var out = new ArrayList<ExportMetricsRequest>(input.size());
    for (var req : input) {
      out.add(rewrite(req));
    }
    return out;
  }

  private ExportMetricsRequest rewrite(ExportMetricsRequest req) {
    var rewrittenTags = rewriteTags(req.getTags());
    return ExportMetricsRequest.builder()
        .metricName(pipeline.rewrite(req.getMetricName()))
        .tags(rewrittenTags)
        .type(req.getType())
        .unit(req.getUnit())
        .gauge(req.getGauge())
        .histo(req.getHisto())
        .sum(req.getSum())
        .build();
  }

  private Map<String, String> rewriteTags(Map<String, String> tags) {
    if (tags == null || tags.isEmpty()) {
      return tags;
    }
    var out = new LinkedHashMap<String, String>(tags.size());
    for (var entry : tags.entrySet()) {
      out.put(pipeline.rewrite(entry.getKey()), entry.getValue());
    }
    return out;
  }
}
