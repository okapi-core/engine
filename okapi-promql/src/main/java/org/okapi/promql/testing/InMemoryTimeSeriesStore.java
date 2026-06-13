/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.promql.eval.HistogramSeries;
import org.okapi.promql.eval.HistogramSeries.FloatSample;
import org.okapi.promql.eval.HistogramSeries.NativeHistogramSample;
import org.okapi.promql.eval.HistogramSeries.SeriesSample;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
      List<IngestedSeries> matches = new ArrayList<>();
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalize(series);
        if (!id.metric().equals(name)) continue;
        if (!id.labels().tags().equals(tags)) continue;
        matches.add(series);
      }
      if (matches.stream().anyMatch(s -> s.metricType() == TestMetricClassifier.MetricType.HISTOGRAM)) {
        return buildHistogramSeries(matches, startMs, endMs);
      }
      if (!matches.isEmpty()) return buildGaugeScan(matches, startMs, endMs);
      return GaugeScan.builder().universalPath(name).timestamps(List.of()).values(List.of()).build();
    }

    private GaugeScan buildGaugeScan(List<IngestedSeries> fragments, long startMs, long endMs) {
      Map<Long, Double> points = new TreeMap<>();
      for (IngestedSeries series : fragments) {
        long ts = series.startMs();
        for (Double value : expandPoints(series.points())) {
          if (ts >= startMs && ts <= endMs && value != null) {
            points.put(ts, value);
          }
          ts += series.stepMs();
        }
      }
      return GaugeScan.builder()
          .universalPath(fragments.get(0).metric())
          .timestamps(new ArrayList<>(points.keySet()))
          .values(new ArrayList<>(points.values()))
          .build();
    }

    private HistogramSeries buildHistogramSeries(
        List<IngestedSeries> fragments, long startMs, long endMs) {
      Map<Long, SeriesSample> points = new TreeMap<>();
      for (IngestedSeries series : fragments) {
        long ts = series.startMs();
        for (PointExpr point : expandSeriesPoints(series.points())) {
          if (ts >= startMs && ts <= endMs && point != null) {
            points.put(ts, toSeriesSample(ts, point));
          }
          ts += series.stepMs();
        }
      }
      return new HistogramSeries(fragments.get(0).metric(), new ArrayList<>(points.values()));
    }

    // Expands PointExpr list to Double values for gauge ingestion.
    // MissingPoint → null (position skipped), StalePoint → stale sentinel.
    private List<Double> expandPoints(List<PointExpr> points) {
      List<Double> out = new ArrayList<>();
      for (PointExpr p : points) expandIngestedPoint(p, out);
      return out;
    }

    private void expandIngestedPoint(PointExpr point, List<Double> out) {
      switch (point) {
        case NumberPoint np -> out.add((double) np.value());
        case NaNPoint ignored -> out.add(Double.NaN);
        case InfPoint ip -> out.add(ip.negative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        case MissingPoint ignored -> out.add(null);
        case StalePoint ignored -> out.add(Staleness.staleDouble());
        case RepeatPoint rp -> { for (int i = 0; i <= rp.count(); i++) expandIngestedPoint(rp.value(), out); }
        case StepSequencePoint sp -> {
          double start = extractNumber(sp.start()), delta = extractNumber(sp.step());
          for (int i = 0; i <= sp.count(); i++) out.add((double) (start + delta * i));
        }
        case HistogramPoint ignored -> throw new IllegalStateException("histogram point in gauge expansion");
      }
    }

    private List<PointExpr> expandSeriesPoints(List<PointExpr> points) {
      List<PointExpr> out = new ArrayList<>();
      for (PointExpr p : points) expandSeriesPoint(p, out);
      return out;
    }

    private void expandSeriesPoint(PointExpr point, List<PointExpr> out) {
      switch (point) {
        case HistogramPoint hp -> out.add(hp);
        case NumberPoint np -> out.add(np);
        case NaNPoint np -> out.add(np);
        case InfPoint ip -> out.add(ip);
        case MissingPoint mp -> out.add(null);
        case StalePoint st -> out.add(st);
        case RepeatPoint rp -> { for (int i = 0; i <= rp.count(); i++) expandSeriesPoint(rp.value(), out); }
        case StepSequencePoint sp -> {
          if (sp.start() instanceof HistogramPoint start && sp.step() instanceof HistogramPoint delta) {
            if (!hasHistogramStructure(start.value()) && !hasHistogramStructure(delta.value())) {
              double startSum = histogramField(start.value(), "sum");
              double startCount = histogramField(start.value(), "count");
              double dSum = histogramField(delta.value(), "sum");
              double dCount = histogramField(delta.value(), "count");
              for (int i = 0; i <= sp.count(); i++) {
                out.add(new HistogramPoint(buildHistogramLiteral(startSum + dSum * i, startCount + dCount * i)));
              }
              break;
            }
            NativeHistogramSample current = toNativeHistogramSample(0L, start.value());
            NativeHistogramSample increment = toNativeHistogramSample(0L, delta.value());
            for (int i = 0; i <= sp.count(); i++) {
              out.add(new HistogramPoint(toHistogramLiteral(current)));
              current = (NativeHistogramSample) HistogramSeries.add(current, increment);
            }
          } else if (sp.start() instanceof NumberPoint start && sp.step() instanceof NumberPoint delta) {
            for (int i = 0; i <= sp.count(); i++)
              out.add(new NumberPoint(start.value() + delta.value() * i));
          } else {
            throw new IllegalStateException("step sequence: start and step must have the same sample type");
          }
        }
      }
    }

    private SeriesSample toSeriesSample(long ts, PointExpr point) {
      return switch (point) {
        case HistogramPoint hp -> toNativeHistogramSample(ts, hp.value());
        case NumberPoint np -> new FloatSample(ts, ts, (double) np.value());
        case NaNPoint ignored -> new FloatSample(ts, ts, Double.NaN);
        case InfPoint ip ->
            new FloatSample(ts, ts, ip.negative() ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        case StalePoint ignored -> new FloatSample(ts, ts, Staleness.staleDouble());
        default -> throw new IllegalStateException("unexpected expanded series point: " + point);
      };
    }
  }

  // ---------- SeriesDiscovery ----------

  private final class StoreDiscovery implements SeriesDiscovery {
    @Override
    public List<SeriesId> expand(String metricOrNull, List<LabelMatcher> matchers, long start, long end) {
      var results = new LinkedHashSet<SeriesId>();
      for (IngestedSeries series : ingestor.series()) {
        SeriesId id = normalize(series);
        if (metricOrNull != null && !metricOrNull.equals(id.metric())) continue;
        if (matchesAll(id, matchers)) results.add(id);
      }
      return List.copyOf(results);
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

  static double histogramField(HistogramLiteral literal, String key) {
    HistogramValue v = literal.fields().get(key);
    if (v instanceof HistogramNumber n) return (double) n.value();
    return Double.NaN;
  }

  private static NativeHistogramSample toNativeHistogramSample(long ts, HistogramLiteral literal) {
    return new NativeHistogramSample(
        ts,
        ts,
        (int) histogramField(literal, "schema"),
        histogramDoubleField(literal, "z_bucket_w"),
        histogramDoubleField(literal, "z_bucket"),
        (int) histogramField(literal, "offset"),
        histogramBuckets(literal, "buckets"),
        (int) histogramField(literal, "n_offset"),
        histogramBuckets(literal, "n_buckets"),
        histogramBuckets(literal, "custom_values"),
        histogramDoubleField(literal, "sum"),
        histogramDoubleField(literal, "count"),
        histogramIdentifier(literal, "counter_reset_hint"));
  }

  private static double[] histogramBuckets(HistogramLiteral literal, String key) {
    HistogramValue value = literal.fields().get(key);
    if (!(value instanceof HistogramNumberList list)) return new double[0];
    double[] buckets = new double[list.values().size()];
    for (int i = 0; i < buckets.length; i++) buckets[i] = list.values().get(i);
    return buckets;
  }

  private static double histogramDoubleField(HistogramLiteral literal, String key) {
    HistogramValue value = literal.fields().get(key);
    return value instanceof HistogramNumber number ? number.value() : 0d;
  }

  private static String histogramIdentifier(HistogramLiteral literal, String key) {
    HistogramValue value = literal.fields().get(key);
    return value instanceof HistogramIdentifier identifier ? identifier.value() : null;
  }

  static HistogramLiteral buildHistogramLiteral(double sum, double count) {
    Map<String, HistogramValue> fields = new HashMap<>();
    fields.put("sum", new HistogramNumber(sum));
    fields.put("count", new HistogramNumber(count));
    return new HistogramLiteral(fields);
  }

  private static HistogramLiteral toHistogramLiteral(NativeHistogramSample histogram) {
    Map<String, HistogramValue> fields = new HashMap<>();
    fields.put("schema", new HistogramNumber(histogram.schema()));
    fields.put("z_bucket_w", new HistogramNumber(histogram.zeroThreshold()));
    fields.put("z_bucket", new HistogramNumber(histogram.zeroCount()));
    fields.put("offset", new HistogramNumber(histogram.positiveOffset()));
    fields.put("buckets", new HistogramNumberList(toList(histogram.positiveBuckets())));
    fields.put("n_offset", new HistogramNumber(histogram.negativeOffset()));
    fields.put("n_buckets", new HistogramNumberList(toList(histogram.negativeBuckets())));
    fields.put("custom_values", new HistogramNumberList(toList(histogram.customValues())));
    fields.put("sum", new HistogramNumber(histogram.sum()));
    fields.put("count", new HistogramNumber(histogram.count()));
    if (histogram.counterResetHint() != null)
      fields.put("counter_reset_hint", new HistogramIdentifier(histogram.counterResetHint()));
    return new HistogramLiteral(fields);
  }

  private static List<Double> toList(double[] values) {
    List<Double> result = new ArrayList<>(values.length);
    for (double value : values) result.add(value);
    return result;
  }

  private static boolean hasHistogramStructure(HistogramLiteral histogram) {
    return histogram.fields().keySet().stream()
        .anyMatch(key -> !key.equals("sum") && !key.equals("count"));
  }

  private static double extractNumber(PointExpr point) {
    if (point instanceof NumberPoint np) return np.value();
    throw new IllegalStateException("expected number point");
  }
}
