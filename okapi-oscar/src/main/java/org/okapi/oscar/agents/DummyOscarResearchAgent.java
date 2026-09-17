/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.okapi.metrics.common.MetricsPathParser;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.oscar.tools.*;
import org.okapi.rest.metrics.query.GaugeQueryConfig;
import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.rest.metrics.query.GetSumsQueryConfig;
import org.okapi.rest.metrics.query.HistoQueryConfig;
import org.okapi.rest.metrics.query.METRIC_TYPE;
import org.okapi.rest.search.LabelValueFilter;
import org.okapi.rest.search.MetricPath;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;
import org.okapi.rest.traces.TimestampFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dummy")
public class DummyOscarResearchAgent implements SreResearchAgent {

  private static final long MAX_GAP_MILLIS = 100;
  private static final Pattern GREETING_PATTERN =
      Pattern.compile("(?i)^\\s*(hi|hello|hey|yo)\\b.*");
  private static final Pattern TIME_PATTERN =
      Pattern.compile("(?i)^\\s*(what\\s+time\\s+is\\s+it|current\\s+time|time\\s+now)\\b.*");
  private static final Pattern PLOT_METRIC_PATTERN =
      Pattern.compile("(?i)^\\s*plot\\s+metric\\s*:\\s*(.+)$");
  private static final Pattern FETCH_TRACE_PATTERN =
      Pattern.compile("(?i)^\\s*fetch\\s+trace\\s*:\\s*(.+)$");

  private static final String KEY_METRIC_PATH = "metricPath";
  private static final String KEY_PARSED_METRIC = "parsedMetric";
  private static final String KEY_TIME_RANGE = "timeRange";
  private static final String KEY_SEARCH_RESPONSE = "searchResponse";
  private static final String KEY_SELECTED_METRIC = "selectedMetric";
  private static final String KEY_GET_METRICS_REQUEST = "getMetricsRequest";
  private static final String KEY_TRACE_ID = "traceId";
  private static final String KEY_TRACE_TIME_RANGE = "traceTimeRange";
  private static final String KEY_TRACE_TIME_RANGE_NANOS = "traceTimeRangeNanos";
  private static final String KEY_SPAN_RESPONSE = "spanResponse";

  private final DateTimeTools dateTimeTools;
  private final GreetingTools greetingTools;
  private final FilterContributionTool filterContributionTool;
  private final StatefulToolFactory statefulToolFactory;

  private final List<SequenceMatcher> matchers;
  private final CannedToolCallSequence fallbackSequence;

  public DummyOscarResearchAgent(
      DateTimeTools dateTimeTools,
      GreetingTools greetingTools,
      FilterContributionTool filterContributionTool,
      StatefulToolFactory statefulToolFactory) {
    this.dateTimeTools = dateTimeTools;
    this.greetingTools = greetingTools;
    this.filterContributionTool = filterContributionTool;
    this.statefulToolFactory = statefulToolFactory;
    this.matchers =
        List.of(
            new SequenceMatcher(GREETING_PATTERN, buildGreetingSequence(), null),
            new SequenceMatcher(TIME_PATTERN, buildTimeSequence(), null),
            new SequenceMatcher(
                PLOT_METRIC_PATTERN,
                buildPlotMetricSequence(),
                (matcher, context) -> context.put(KEY_METRIC_PATH, matcher.group(1).trim())),
            new SequenceMatcher(
                FETCH_TRACE_PATTERN,
                buildFetchTraceSequence(),
                (matcher, context) -> context.put(KEY_TRACE_ID, matcher.group(1).trim())));
    this.fallbackSequence = buildFallbackSequence();
  }

  @Override
  public void respond(String sessionId, long streamId, String userMessage) {
    var toolContext = statefulToolFactory.getTools(sessionId, streamId);
    var context =
        new CannedToolCallSequence.Context(
            sessionId,
            streamId,
            userMessage,
            toolContext.getMetricsTools(),
            toolContext.getTracingTools(),
            toolContext.getLogsSearchTool(),
            dateTimeTools,
            greetingTools,
            filterContributionTool,
            toolContext.getStatefulTools());
    for (var matcher : matchers) {
      var match = matcher.pattern().matcher(userMessage);
      if (!match.matches()) {
        continue;
      }
      if (matcher.initializer() != null) {
        matcher.initializer().accept(match, context);
      }
      matcher.sequence().runBlocking(context);
      return;
    }
    fallbackSequence.runBlocking(context);
  }

  private CannedToolCallSequence buildGreetingSequence() {
    return new CannedToolCallSequence(
        "greeting",
        List.of(
            context -> {
              var greeting = context.getGreetingTools().randomGreeting();
              context.getStatefulTools().postResponse(greeting);
            }),
        MAX_GAP_MILLIS);
  }

  private CannedToolCallSequence buildTimeSequence() {
    return new CannedToolCallSequence(
        "time",
        List.of(
            context -> {
              long nowMs = context.getDateTimeTools().currentTime();
              String iso =
                  context.getDateTimeTools().linuxEpochToHumanReadableTimestamp(nowMs / 1000);
              context.getStatefulTools().postResponse("Current time: " + iso);
            }),
        MAX_GAP_MILLIS);
  }

  private CannedToolCallSequence buildPlotMetricSequence() {
    return new CannedToolCallSequence(
        "plot-metric",
        List.of(
            this::parseMetricPath,
            this::captureTimeRange,
            this::searchMetricPath,
            this::selectMetricPath,
            this::getMetricsForSelectedPath),
        MAX_GAP_MILLIS);
  }

  private CannedToolCallSequence buildFallbackSequence() {
    return new CannedToolCallSequence(
        "fallback",
        List.of(
            context ->
                context
                    .getStatefulTools()
                    .postResponse("Dummy agent: no canned sequence for that prompt.")),
        MAX_GAP_MILLIS);
  }

  private CannedToolCallSequence buildFetchTraceSequence() {
    return new CannedToolCallSequence(
        "fetch-trace",
        List.of(
            this::captureTraceTimeRange,
            this::fetchTraceSpans,
            this::postTraceFollowUp,
            this::postTraceSummary),
        MAX_GAP_MILLIS);
  }

  private void parseMetricPath(CannedToolCallSequence.Context context) {
    String metricPath = context.get(KEY_METRIC_PATH, String.class);
    if (metricPath == null || metricPath.isBlank()) {
      context.getStatefulTools().postResponse("No metric path provided.");
      context.abort();
      return;
    }
    var parsed = MetricsPathParser.parseNormal(metricPath);
    if (parsed.isEmpty()) {
      parsed = MetricsPathParser.parse(metricPath);
    }
    if (parsed.isEmpty()) {
      context.getStatefulTools().postResponse("Unable to parse metric path: " + metricPath);
      context.abort();
      return;
    }
    context.put(KEY_PARSED_METRIC, parsed.get());
  }

  private void captureTimeRange(CannedToolCallSequence.Context context) {
    var range = context.getDateTimeTools().timeRange(1, TimeUnit.HOURS);
    context.put(KEY_TIME_RANGE, range);
  }

  private void searchMetricPath(CannedToolCallSequence.Context context) {
    var parsed = context.get(KEY_PARSED_METRIC, MetricsPathParser.MetricsRecord.class);
    var range = context.get(KEY_TIME_RANGE, DateTimeTools.TimeRange.class);
    if (parsed == null || range == null) {
      context.getStatefulTools().postResponse("Missing metric metadata to search.");
      context.abort();
      return;
    }
    List<LabelValueFilter> filters = new ArrayList<>();
    for (var entry : parsed.tags().entrySet()) {
      filters.add(LabelValueFilter.builder().label(entry.getKey()).value(entry.getValue()).build());
    }
    var request =
        SearchMetricsRequest.builder()
            .metricName(parsed.name())
            .valueFilters(filters.isEmpty() ? null : filters)
            .tsStartMillis(range.getStartMs())
            .tsEndMillis(range.getEndMs())
            .build();
    var response = context.getMetricsTools().searchMetrics(request);
    context.put(KEY_SEARCH_RESPONSE, response);
  }

  private void selectMetricPath(CannedToolCallSequence.Context context) {
    var parsed = context.get(KEY_PARSED_METRIC, MetricsPathParser.MetricsRecord.class);
    var response = context.get(KEY_SEARCH_RESPONSE, SearchMetricsV2Response.class);
    if (parsed == null || response == null || response.getMatchingPaths() == null) {
      context.getStatefulTools().postResponse("No matching metrics found.");
      context.abort();
      return;
    }
    var matching = response.getMatchingPaths();
    if (matching.isEmpty()) {
      context.getStatefulTools().postResponse("No matching metrics found.");
      context.abort();
      return;
    }
    var selected = pickMetricPath(matching, parsed.tags());
    context.put(KEY_SELECTED_METRIC, selected);
  }

  private void getMetricsForSelectedPath(CannedToolCallSequence.Context context) {
    var path = context.get(KEY_SELECTED_METRIC, MetricPath.class);
    var range = context.get(KEY_TIME_RANGE, DateTimeTools.TimeRange.class);
    if (path == null || range == null) {
      context.getStatefulTools().postResponse("No metric path selected.");
      context.abort();
      return;
    }
    if (path.getMetricType() == null) {
      context.getStatefulTools().postResponse("Metric type missing for selected path.");
      context.abort();
      return;
    }
    if (path.getLabels() == null) {
      context.getStatefulTools().postResponse("Metric labels missing for selected path.");
      context.abort();
      return;
    }
    var builder =
        GetMetricsRequest.builder()
            .metric(path.getMetric())
            .tags(path.getLabels())
            .start(range.getStartMs())
            .end(range.getEndMs())
            .metricType(path.getMetricType());
    if (path.getMetricType() == METRIC_TYPE.GAUGE) {
      builder.gaugeQueryConfig(new GaugeQueryConfig(RES_TYPE.MINUTELY, AGG_TYPE.AVG));
    } else if (path.getMetricType() == METRIC_TYPE.HISTO) {
      builder.histoQueryConfig(
          HistoQueryConfig.builder()
              .temporality(mapHistoTemporality(path.getTemporality()))
              .build());
    } else if (path.getMetricType() == METRIC_TYPE.SUM) {
      builder.sumsQueryConfig(
          GetSumsQueryConfig.builder()
              .temporality(mapSumTemporality(path.getTemporality()))
              .build());
    }
    var request = builder.build();
    context.put(KEY_GET_METRICS_REQUEST, request);
    context.getMetricsTools().getMetrics(request);
    context.getStatefulTools().postPlotMetricFollowUp(request);
    context.getStatefulTools().postResponse("Plotted metric: " + path.getMetric());
  }

  private void captureTraceTimeRange(CannedToolCallSequence.Context context) {
    var rangeMs = context.getDateTimeTools().timeRange(1, TimeUnit.HOURS);
    var rangeNanos = context.getDateTimeTools().timeRangeNanos(1, TimeUnit.HOURS);
    context.put(KEY_TRACE_TIME_RANGE, rangeMs);
    context.put(KEY_TRACE_TIME_RANGE_NANOS, rangeNanos);
  }

  private void fetchTraceSpans(CannedToolCallSequence.Context context) {
    var traceId = context.get(KEY_TRACE_ID, String.class);
    var rangeNanos = context.get(KEY_TRACE_TIME_RANGE_NANOS, DateTimeTools.TimeRangeNanos.class);
    if (traceId == null || traceId.isBlank() || rangeNanos == null) {
      context.getStatefulTools().postResponse("Missing trace ID or time range.");
      context.abort();
      return;
    }
    var request =
        SpanQueryV2Request.builder()
            .traceId(traceId)
            .timestampFilter(
                TimestampFilter.builder()
                    .tsStartNanos(rangeNanos.getStartNanos())
                    .tsEndNanos(rangeNanos.getEndNanos())
                    .build())
            .build();
    SpanQueryV2Response response = context.getTracingTools().getSpans(request);
    context.put(KEY_SPAN_RESPONSE, response);
  }

  private void postTraceFollowUp(CannedToolCallSequence.Context context) {
    var traceId = context.get(KEY_TRACE_ID, String.class);
    var rangeNanos = context.get(KEY_TRACE_TIME_RANGE_NANOS, DateTimeTools.TimeRangeNanos.class);
    if (traceId == null || rangeNanos == null) {
      context.getStatefulTools().postResponse("Missing trace follow-up metadata.");
      context.abort();
      return;
    }
    context
        .getStatefulTools()
        .postGetTraceFollowUp(traceId, rangeNanos.getStartNanos(), rangeNanos.getEndNanos());
  }

  private void postTraceSummary(CannedToolCallSequence.Context context) {
    var response = context.get(KEY_SPAN_RESPONSE, SpanQueryV2Response.class);
    var traceId = context.get(KEY_TRACE_ID, String.class);
    int count = response == null || response.getItems() == null ? 0 : response.getItems().size();
    if (traceId == null || traceId.isBlank()) {
      context.getStatefulTools().postResponse("Found " + count + " spans.");
      return;
    }
    context.getStatefulTools().postResponse("Found " + count + " spans for trace " + traceId + ".");
  }

  private static MetricPath pickMetricPath(
      List<MetricPath> matching, Map<String, String> desiredTags) {
    if (desiredTags == null || desiredTags.isEmpty()) {
      return matching.get(0);
    }
    for (var path : matching) {
      if (path.getLabels() == null) {
        continue;
      }
      if (path.getLabels().entrySet().containsAll(desiredTags.entrySet())) {
        return path;
      }
    }
    return matching.get(0);
  }

  private static HistoQueryConfig.TEMPORALITY mapHistoTemporality(String temporality) {
    if (temporality == null) {
      return HistoQueryConfig.TEMPORALITY.MERGED;
    }
    return switch (temporality.toUpperCase(Locale.ROOT)) {
      case "CUMULATIVE" -> HistoQueryConfig.TEMPORALITY.CUMULATIVE;
      case "DELTA" -> HistoQueryConfig.TEMPORALITY.DELTA;
      case "MERGED" -> HistoQueryConfig.TEMPORALITY.MERGED;
      default -> HistoQueryConfig.TEMPORALITY.MERGED;
    };
  }

  private static GetSumsQueryConfig.TEMPORALITY mapSumTemporality(String temporality) {
    if (temporality == null) {
      return GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE;
    }
    return switch (temporality.toUpperCase(Locale.ROOT)) {
      case "CUMULATIVE" -> GetSumsQueryConfig.TEMPORALITY.CUMULATIVE;
      case "DELTA", "DELTA_AGGREGATE" -> GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE;
      default -> GetSumsQueryConfig.TEMPORALITY.DELTA_AGGREGATE;
    };
  }

  private record SequenceMatcher(
      Pattern pattern, CannedToolCallSequence sequence, SequenceInitializer initializer) {}

  private interface SequenceInitializer {
    void accept(java.util.regex.Matcher matcher, CannedToolCallSequence.Context context);
  }
}
