/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.Staleness;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.eval.ts.SeriesDiscovery;
import org.okapi.promql.eval.ts.TsClient;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.testing.PromQlTestAst.*;
import org.okapi.promql.testing.PromQlTestIngestor.IngestedSeries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * In-memory storage layer for the PromQL test evaluator. Wraps {@link InMemoryPromQlTestIngestor}
 * and exposes {@link TsClient} and {@link SeriesDiscovery} backed by ingested test data.
 */
final class InMemoryTimeSeriesStore {

  private static final long DEFAULT_START_MS = 0L;

  private final InMemoryPromQlTestIngestor ingestor = new InMemoryPromQlTestIngestor();
  final TsClient tsClient = new StoreTsClient();
  final SeriesDiscovery discovery = new StoreDiscovery();

  void clear() {
    ingestor.clear();
  }

  void ingest(LoadCmd loadCmd) {
    ingestor.ingestLoad(DEFAULT_START_MS, loadCmd);
  }

  // ---------- TsClient ----------

  private final class StoreTsClient implements TsClient {
    @Override
    public Scan get(String name, Map<String, String> tags, RESOLUTION res, long startMs, long endMs) {
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalize(series);
        if (!id.metric().equals(name)) continue;
        if (!id.labels().tags().equals(tags)) continue;
        if (series.metricType() == TestMetricClassifier.MetricType.HISTOGRAM) {
          return buildHistogramSeries(series, startMs, endMs);
        }
        return buildGaugeScan(series, startMs, endMs);
      }
      return GaugeScan.builder().universalPath(name).timestamps(List.of()).values(List.of()).build();
    }

    private GaugeScan buildGaugeScan(IngestedSeries series, long startMs, long endMs) {
      List<Long> timestamps = new ArrayList<>();
      List<Float> values = new ArrayList<>();
      long ts = series.startMs();
      for (Float value : expandPoints(series.points())) {
        if (ts >= startMs && ts <= endMs && value != null) {
          timestamps.add(ts);
          values.add(value);
        }
        ts += series.stepMs();
      }
      return GaugeScan.builder()
          .universalPath(series.metric())
          .timestamps(timestamps)
          .values(values)
          .build();
    }

    private HistogramSeries buildHistogramSeries(IngestedSeries series, long startMs, long endMs) {
      List<HistogramSeries.HistogramPoint> points = new ArrayList<>();
      long ts = series.startMs();
      for (HistogramLiteral literal : expandHistogramPoints(series.points())) {
        if (ts >= startMs && ts <= endMs && literal != null) {
          float sum = histogramField(literal, "sum");
          float count = histogramField(literal, "count");
          points.add(new HistogramSeries.HistogramPoint(ts, ts, null, null, sum, count));
        }
        ts += series.stepMs();
      }
      return new HistogramSeries(series.metric(), points);
    }

    // Expands PointExpr list to Float values for gauge ingestion.
    // MissingPoint → null (position skipped), StalePoint → stale sentinel.
    private List<Float> expandPoints(List<PointExpr> points) {
      List<Float> out = new ArrayList<>();
      for (PointExpr p : points) expandIngestedPoint(p, out);
      return out;
    }

    private void expandIngestedPoint(PointExpr point, List<Float> out) {
      switch (point) {
        case NumberPoint np -> out.add((float) np.value());
        case NaNPoint ignored -> out.add(Float.NaN);
        case InfPoint ip -> out.add(ip.negative() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY);
        case MissingPoint ignored -> out.add(null);
        case StalePoint ignored -> out.add(Staleness.staleFloat());
        case RepeatPoint rp -> { for (int i = 0; i < rp.count(); i++) expandIngestedPoint(rp.value(), out); }
        case StepSequencePoint sp -> {
          double start = extractNumber(sp.start()), delta = extractNumber(sp.step());
          for (int i = 0; i <= sp.count(); i++) out.add((float) (start + delta * i));
        }
        case HistogramPoint ignored -> throw new IllegalStateException("histogram point in gauge expansion");
      }
    }

    private List<HistogramLiteral> expandHistogramPoints(List<PointExpr> points) {
      List<HistogramLiteral> out = new ArrayList<>();
      for (PointExpr p : points) expandHistogramPoint(p, out);
      return out;
    }

    private void expandHistogramPoint(PointExpr point, List<HistogramLiteral> out) {
      switch (point) {
        case HistogramPoint hp -> out.add(hp.value());
        case MissingPoint mp -> out.add(null);
        case StalePoint st -> out.add(null);
        case RepeatPoint rp -> { for (int i = 0; i < rp.count(); i++) expandHistogramPoint(rp.value(), out); }
        case StepSequencePoint sp -> {
          if (sp.start() instanceof HistogramPoint start && sp.step() instanceof HistogramPoint delta) {
            float startSum = histogramField(start.value(), "sum"), startCount = histogramField(start.value(), "count");
            float dSum = histogramField(delta.value(), "sum"), dCount = histogramField(delta.value(), "count");
            for (int i = 0; i <= sp.count(); i++) {
              out.add(buildHistogramLiteral(startSum + dSum * i, startCount + dCount * i));
            }
          } else {
            throw new IllegalStateException("histogram step sequence: start and step must both be histogram points");
          }
        }
        default -> throw new IllegalStateException("expected histogram point, got " + point.getClass().getSimpleName());
      }
    }
  }

  // ---------- SeriesDiscovery ----------

  private final class StoreDiscovery implements SeriesDiscovery {
    @Override
    public List<SeriesId> expand(String metricOrNull, List<LabelMatcher> matchers, long start, long end) {
      List<SeriesId> results = new ArrayList<>();
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalize(series);
        if (metricOrNull != null && !metricOrNull.equals(id.metric())) continue;
        if (matchesAll(id, matchers)) results.add(id);
      }
      return results;
    }

    private boolean matchesAll(SeriesId id, List<LabelMatcher> matchers) {
      for (LabelMatcher m : matchers) if (!matches(id, m)) return false;
      return true;
    }

    private boolean matches(SeriesId id, LabelMatcher m) {
      String actual = "__name__".equals(m.name()) ? id.metric() : id.labels().tags().get(m.name());
      if (actual == null) actual = "";
      return switch (m.op()) {
        case EQ  -> actual.equals(m.value());
        case NE  -> !actual.equals(m.value());
        case RE  -> Pattern.compile(m.value()).matcher(actual).matches();
        case NRE -> !Pattern.compile(m.value()).matcher(actual).matches();
      };
    }
  }

  // ---------- Shared helpers ----------

  static SeriesId normalize(IngestedSeries series) {
    return normalize(series.metric(), series.labels());
  }

  static SeriesId normalize(SeriesDef series) {
    return normalize(series.metric(), series.labels());
  }

  private static SeriesId normalize(String metric, Map<String, String> rawLabels) {
    Map<String, String> labels = new HashMap<>(rawLabels);
    if (metric == null || metric.isEmpty()) {
      String name = labels.remove("__name__");
      metric = name == null ? "" : name;
    }
    return new SeriesId(metric, new Labels(labels));
  }

  static float histogramField(HistogramLiteral literal, String key) {
    HistogramValue v = literal.fields().get(key);
    if (v instanceof HistogramNumber n) return (float) n.value();
    return Float.NaN;
  }

  static HistogramLiteral buildHistogramLiteral(float sum, float count) {
    Map<String, HistogramValue> fields = new HashMap<>();
    fields.put("sum", new HistogramNumber(sum));
    fields.put("count", new HistogramNumber(count));
    return new HistogramLiteral(fields);
  }

  private static double extractNumber(PointExpr point) {
    if (point instanceof NumberPoint np) return np.value();
    throw new IllegalStateException("expected number point");
  }
}
