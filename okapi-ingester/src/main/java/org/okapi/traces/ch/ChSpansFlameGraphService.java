/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.clickhouse.client.api.Client;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.traces.FlameGraphNode;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpansFlameGraphResponse;
import org.okapi.spring.configs.Profiles;
import org.okapi.traces.ch.template.ChTraceTemplateEngine;
import org.okapi.validation.OkapiChecks;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
public class ChSpansFlameGraphService {
  private final Client client;
  private final ChTraceTemplateEngine templateEngine;

  public ChSpansFlameGraphService(Client client, ChTraceTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public SpansFlameGraphResponse queryFlameGraph(SpanQueryV2Request request) {
    OkapiChecks.checkArgument(request.getTraceId() != null, "TraceId is required");
    var timeFilter = request.getTimestampFilter();
    OkapiChecks.checkArgument(
        timeFilter != null, "Timestamp filter is required to get a flamegraph");

    var traceIdHex = request.getTraceId();
    long startNs = timeFilter.getTsStartNanos();
    long endNs = timeFilter.getTsEndNanos();
    var template = buildTemplate(traceIdHex, startNs, endNs);
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_V2, template);
    var records = client.queryAll(query);

    var spans = new ArrayList<SpanInfo>(records.size());
    for (var record : records) {
      var spanId = record.getString("span_id");
      if (spanId == null || spanId.isEmpty()) continue;
      var info =
          SpanInfo.builder()
              .spanId(spanId)
              .parentSpanId(record.getString("parent_span_id"))
              .startNs(record.getLong("ts_start_ns"))
              .endNs(record.getLong("ts_end_ns"))
              .serviceName(record.getString("service_name"))
              .kind(record.getString("kind"))
              .build();
      spans.add(info);
    }
    return buildFlameGraph(traceIdHex, startNs, endNs, spans);
  }

  private static ChSpansQueryTemplate buildTemplate(String traceId, long startNs, long endNs) {
    return ChSpansQueryTemplate.builder()
        .table(ChConstants.TBL_SPANS_V1)
        .traceId(traceId)
        .tsStartNs(startNs)
        .tsEndNs(endNs)
        .limit(ChConstants.TRACE_QUERY_LIMIT)
        .build();
  }

  private static void sortNodesByStartNs(List<FlameGraphNode> nodes) {
    if (nodes == null || nodes.isEmpty()) return;
    nodes.sort(Comparator.comparingLong(FlameGraphNode::getStartNs));
    for (var node : nodes) {
      sortNodesByStartNs(node.getChildren());
    }
  }

  static SpansFlameGraphResponse buildFlameGraph(
      String traceIdHex, long queryStartNs, long queryEndNs, List<SpanInfo> spans) {
    if (spans == null || spans.isEmpty()) {
      return SpansFlameGraphResponse.builder()
          .traceId(traceIdHex)
          .traceStartNs(queryStartNs)
          .traceEndNs(queryEndNs)
          .roots(List.of())
          .build();
    }

    long traceStartNs = Long.MAX_VALUE;
    long traceEndNs = Long.MIN_VALUE;
    for (var span : spans) {
      traceStartNs = Math.min(traceStartNs, span.getStartNs());
      traceEndNs = Math.max(traceEndNs, span.getEndNs());
    }

    var nodesById = new HashMap<String, FlameGraphNode>(spans.size());
    for (var span : spans) {
      long durationNs = Math.max(0L, span.getEndNs() - span.getStartNs());
      long offsetNs = span.getStartNs() - traceStartNs;
      var node =
          FlameGraphNode.builder()
              .spanId(span.getSpanId())
              .parentSpanId(span.getParentSpanId())
              .serviceName(span.getServiceName())
              .kind(span.getKind())
              .startNs(span.getStartNs())
              .endNs(span.getEndNs())
              .durationNs(durationNs)
              .offsetNs(offsetNs)
              .children(new ArrayList<>())
              .build();
      nodesById.put(span.getSpanId(), node);
    }

    var roots = new ArrayList<FlameGraphNode>();
    for (var node : nodesById.values()) {
      var parentId = node.getParentSpanId();
      var parent = parentId == null || parentId.isEmpty() ? null : nodesById.get(parentId);
      if (parent == null) {
        roots.add(node);
      } else {
        parent.getChildren().add(node);
      }
    }

    sortNodesByStartNs(roots);

    return SpansFlameGraphResponse.builder()
        .traceId(traceIdHex)
        .traceStartNs(traceStartNs)
        .traceEndNs(traceEndNs)
        .roots(roots)
        .build();
  }
}
