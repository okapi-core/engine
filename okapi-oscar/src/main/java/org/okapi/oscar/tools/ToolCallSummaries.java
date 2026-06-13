package org.okapi.oscar.tools;

import org.okapi.rest.metrics.query.*;
import org.okapi.rest.search.SearchMetricsRequest;
import org.okapi.rest.search.SearchMetricsV2Response;
import org.okapi.rest.traces.SpanQueryV2Request;
import org.okapi.rest.traces.SpanQueryV2Response;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ToolCallSummaries {

  private ToolCallSummaries() {}

  public static String summarizeSpanQueryRequest(SpanQueryV2Request request) {
    if (request == null) {
      return "Fetching spans.";
    }
    var builder = new StringBuilder("Fetching spans:");
    appendIfPresent(builder, " traceId=", request.getTraceId());
    appendIfPresent(builder, " spanId=", request.getSpanId());
    appendIfPresent(builder, " kind=", request.getKind());
    if (request.getServiceFilter() != null) {
      appendIfPresent(builder, " service=", request.getServiceFilter().getService());
      appendIfPresent(builder, " peer=", request.getServiceFilter().getPeer());
    }
    if (request.getTimestampFilter() != null) {
      builder
          .append(" timeNs=[")
          .append(request.getTimestampFilter().getTsStartNanos())
          .append(",")
          .append(request.getTimestampFilter().getTsEndNanos())
          .append("]");
    }
    int httpCount = countHttpFilters(request.getHttpFilters());
    if (httpCount > 0) {
      builder.append(" httpFilters=").append(httpCount);
    }
    int dbCount = countDbFilters(request.getDbFilters());
    if (dbCount > 0) {
      builder.append(" dbFilters=").append(dbCount);
    }
    int stringAttrs =
        request.getStringAttributesFilter() == null
            ? 0
            : request.getStringAttributesFilter().size();
    int numberAttrs =
        request.getNumberAttributesFilter() == null
            ? 0
            : request.getNumberAttributesFilter().size();
    if (stringAttrs > 0 || numberAttrs > 0) {
      builder.append(" stringAttrs=").append(stringAttrs);
      builder.append(" numberAttrs=").append(numberAttrs);
    }
    return builder.toString();
  }

  public static String summarizeSpanQueryResponse(SpanQueryV2Response response) {
    int count = response == null || response.getItems() == null ? 0 : response.getItems().size();
    return "Fetch span results: spans=" + count;
  }

  public static String summarizeDiscoverPeersRequest(String service, long startNs, long endNs) {
    var builder = new StringBuilder("Discovering peers:");
    appendIfPresent(builder, " service=", service);
    builder.append(" timeNs=[").append(startNs).append(",").append(endNs).append("]");
    return builder.toString();
  }

  public static String summarizeDiscoverPeersResponse(List<String> peers) {
    int count = peers == null ? 0 : peers.size();
    return "Discover peers results: peers=" + count;
  }

  public static String summarizeSearchMetricsRequest(SearchMetricsRequest request) {
    if (request == null) {
      return "Searching metrics.";
    }
    var builder = new StringBuilder("Searching metrics:");
    appendIfPresent(builder, " name=", request.getMetricName());
    appendIfPresent(builder, " pattern=", request.getMetricNamePattern());
    if (request.getAnyMetricOrValueFilter() != null) {
      appendIfPresent(builder, " any=", request.getAnyMetricOrValueFilter().getValue());
      appendIfPresent(builder, " anyPattern=", request.getAnyMetricOrValueFilter().getPattern());
    }
    int valueFilters = request.getValueFilters() == null ? 0 : request.getValueFilters().size();
    int patternFilters =
        request.getPatternFilters() == null ? 0 : request.getPatternFilters().size();
    if (valueFilters > 0 || patternFilters > 0) {
      builder.append(" valueFilters=").append(valueFilters);
      builder.append(" patternFilters=").append(patternFilters);
    }
    builder
        .append(" timeMs=[")
        .append(request.getTsStartMillis())
        .append(",")
        .append(request.getTsEndMillis())
        .append("]");
    return builder.toString();
  }

  public static String summarizeSearchMetricsResponse(SearchMetricsV2Response response) {
    int count =
        response == null || response.getMatchingPaths() == null
            ? 0
            : response.getMatchingPaths().size();
    return "Search metrics results: matches=" + count;
  }

  public static String summarizeSearchMetricsRequest(
      org.okapi.rest.metrics.search.SearchMetricsRequest request) {
    if (request == null) {
      return "Searching metrics.";
    }
    var builder = new StringBuilder("Searching metrics:");
    appendIfPresent(builder, " team=", request.getTeam());
    appendIfPresent(builder, " pattern=", request.getPattern());
    builder
        .append(" timeMs=[")
        .append(request.getStartTime())
        .append(",")
        .append(request.getEndTime())
        .append("]");
    return builder.toString();
  }

  public static String summarizeGetMetricsRequest(GetMetricsRequest request) {
    if (request == null) {
      return "Fetching metrics.";
    }
    var builder = new StringBuilder("Fetching metrics:");
    appendIfPresent(builder, " metric=", request.getMetric());
    int tagCount = request.getTags() == null ? 0 : request.getTags().size();
    builder.append(" tags=").append(tagCount);
    builder
        .append(" timeMs=[")
        .append(request.getStart())
        .append(",")
        .append(request.getEnd())
        .append("]");
    if (request.getMetricType() != null) {
      builder.append(" type=").append(request.getMetricType());
    }
    if (request.getMetricType() == METRIC_TYPE.GAUGE && request.getGaugeQueryConfig() != null) {
      builder.append(" resolution=").append(request.getGaugeQueryConfig().getResolution());
      builder.append(" agg=").append(request.getGaugeQueryConfig().getAggregation());
    } else if (request.getMetricType() == METRIC_TYPE.HISTO
        && request.getHistoQueryConfig() != null) {
      builder.append(" temporality=").append(request.getHistoQueryConfig().getTemporality());
    } else if (request.getMetricType() == METRIC_TYPE.SUM && request.getSumsQueryConfig() != null) {
      builder.append(" temporality=").append(request.getSumsQueryConfig().getTemporality());
    }
    return builder.toString();
  }

  public static String summarizeGetMetricsResponse(
      GetMetricsRequest request, GetMetricsResponse response) {
    if (response == null) {
      return "Fetch metrics results: points=0";
    }
    METRIC_TYPE type = metricTypeFromRequestOrResponse(request, response);
    String timeWindow = summarizeTimeWindow(type, response, request);
    String metricPath = formatMetricPath(response.getMetric(), response.getTags());
    String temporality = summarizeTemporality(type, request);
    return "Fetch metrics results: "
        + (temporality != null ? " temporality=" + temporality : "")
        + " time-ms="
        + timeWindow
        + " metric="
        + metricPath + " "
        + summarizeResponse(type, response);
  }

  public static String summarizeGaugeResponse(GetGaugeResponse gaugeResponse) {
    if (gaugeResponse == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("results=").append(gaugeResponse.getSeries().size());
    sb.append(" num-points=");
    for (var s : gaugeResponse.getSeries()) {
      sb.append(s.getTimes().size()).append(",");
    }
    return sb.toString();
  }

  public static String summarizeHistoResponse(GetHistogramResponse histogramResponse) {
    if (histogramResponse == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("results=").append(histogramResponse.getSeries().size());
    sb.append(" histos=");
    for (var s : histogramResponse.getSeries()) {
      if(s.getHistogram().getBuckets() == null){
        sb.append(" malformed-histogram");
        continue;
      }
      var buckets = (s.getHistogram().getBuckets()).toString();
      var counts = (s.getHistogram().getBuckets()).toString();
      sb.append("[buckets=").append(buckets).append(", counts=").append(counts).append("]");
    }
    return sb.toString();
  }

  public static String summarizeSumResponse(GetSumsResponse res) {
    if (res == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    sb.append("results=").append(res.getSums().size());
    sb.append(" sums=");
    for (var sum : res.getSums()) {
      sb.append("[").append("sum = ").append(sum.getCount()).append(sum.getUnit()).append("],");
    }
    return sb.toString();
  }

  private static METRIC_TYPE metricTypeFromRequestOrResponse(
      GetMetricsRequest request, GetMetricsResponse response) {
    if (request != null && request.getMetricType() != null) {
      return request.getMetricType();
    }
    if (response.getGaugeResponse() != null) {
      return METRIC_TYPE.GAUGE;
    }
    if (response.getHistogramResponse() != null) {
      return METRIC_TYPE.HISTO;
    }
    if (response.getSumsResponse() != null) {
      return METRIC_TYPE.SUM;
    }
    return null;
  }

  private static String summarizeResponse(METRIC_TYPE type, GetMetricsResponse response) {
    if (type == null) {
      return "";
    }
    return switch (type) {
      case METRIC_TYPE.GAUGE -> summarizeGaugeResponse(response.getGaugeResponse());

      case METRIC_TYPE.HISTO -> summarizeHistoResponse(response.getHistogramResponse());

      case METRIC_TYPE.SUM -> summarizeSumResponse(response.getSumsResponse());
    };
  }

  private static String summarizeTimeWindow(
      METRIC_TYPE type, GetMetricsResponse response, GetMetricsRequest request) {
    long[] range = null;
    if (type == METRIC_TYPE.GAUGE) {
      range = gaugeTimeRange(response.getGaugeResponse());
    } else if (type == METRIC_TYPE.HISTO) {
      range = histogramTimeRange(response.getHistogramResponse());
    } else if (type == METRIC_TYPE.SUM) {
      range = sumsTimeRange(response.getSumsResponse());
    }
    if (range == null || range[0] == Long.MAX_VALUE) {
      if (request != null) {
        return "[" + request.getStart() + "," + request.getEnd() + "]";
      }
      return "[n/a]";
    }
    return "[" + range[0] + "," + range[1] + "]";
  }

  private static long[] gaugeTimeRange(GetGaugeResponse response) {
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (GaugeSeries series : response.getSeries()) {
      for (Long time : series.getTimes()) {
        min = Math.min(min, time);
        max = Math.max(max, time);
      }
    }
    return new long[] {min, max};
  }

  private static long[] histogramTimeRange(GetHistogramResponse response) {
    if (response == null) {
      return null;
    }
    if (response.getSeries() != null && !response.getSeries().isEmpty()) {
      long min = Long.MAX_VALUE;
      long max = Long.MIN_VALUE;
      for (HistogramSeries series : response.getSeries()) {
        if (series == null || series.getHistogram() == null) {
          continue;
        }
        var hist = series.getHistogram();
        min = Math.min(min, hist.getStart());
        long end = hist.getEnd() == null ? hist.getStart() : hist.getEnd();
        max = Math.max(max, end);
      }
      return new long[] {min, max};
    }
    return null;
  }

  private static long countHistogramPoints(GetHistogramResponse response) {
    return response.getSeries().size();
  }

  private static long[] sumsTimeRange(GetSumsResponse response) {
    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    for (Sum sum : response.getSums()) {
      if (sum == null) {
        continue;
      }
      min = Math.min(min, sum.getTs());
      max = Math.max(max, sum.getTe());
    }
    return new long[] {min, max};
  }

  private static String summarizeTemporality(METRIC_TYPE type, GetMetricsRequest request) {
    if (type == METRIC_TYPE.HISTO && request != null && request.getHistoQueryConfig() != null) {
      HistoQueryConfig.TEMPORALITY temporality = request.getHistoQueryConfig().getTemporality();
      return temporality == null ? "n/a" : temporality.toString();
    }
    if (type == METRIC_TYPE.SUM && request != null && request.getSumsQueryConfig() != null) {
      GetSumsQueryConfig.TEMPORALITY temporality = request.getSumsQueryConfig().getTemporality();
      return temporality == null ? "n/a" : temporality.toString();
    }
    return "n/a";
  }

  private static String formatMetricPath(String metric, Map<String, String> tags) {
    if (metric == null) {
      return "unknown";
    }
    if (tags == null || tags.isEmpty()) {
      return metric;
    }
    var sorted = new TreeMap<>(tags);
    var builder = new StringBuilder(metric).append("{");
    boolean first = true;
    for (var entry : sorted.entrySet()) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append(entry.getKey()).append("=").append(entry.getValue());
    }
    builder.append("}");
    return builder.toString();
  }

  private static void appendIfPresent(StringBuilder builder, String prefix, String value) {
    if (value != null && !value.isBlank()) {
      builder.append(prefix).append(value);
    }
  }

  private static int countHttpFilters(org.okapi.rest.traces.HttpFilters filters) {
    if (filters == null) {
      return 0;
    }
    int count = 0;
    if (filters.getHttpMethod() != null && !filters.getHttpMethod().isBlank()) {
      count++;
    }
    if (filters.getStatusCode() != null) {
      count++;
    }
    if (filters.getOrigin() != null && !filters.getOrigin().isBlank()) {
      count++;
    }
    if (filters.getHost() != null && !filters.getHost().isBlank()) {
      count++;
    }
    return count;
  }

  private static int countDbFilters(org.okapi.rest.traces.DbFilters filters) {
    if (filters == null) {
      return 0;
    }
    int count = 0;
    if (filters.getSystem() != null && !filters.getSystem().isBlank()) {
      count++;
    }
    if (filters.getCollection() != null && !filters.getCollection().isBlank()) {
      count++;
    }
    if (filters.getNamespace() != null && !filters.getNamespace().isBlank()) {
      count++;
    }
    if (filters.getOperation() != null && !filters.getOperation().isBlank()) {
      count++;
    }
    return count;
  }
}
