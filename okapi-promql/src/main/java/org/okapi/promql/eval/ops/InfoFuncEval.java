/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.ops;

import com.google.re2j.Pattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.okapi.metrics.pojos.results.GaugeScan;
import org.okapi.metrics.pojos.results.Scan;
import org.okapi.promql.eval.*;
import org.okapi.promql.eval.VectorData.Labels;
import org.okapi.promql.eval.VectorData.Sample;
import org.okapi.promql.eval.VectorData.SeriesId;
import org.okapi.promql.eval.VectorData.SeriesSample;
import org.okapi.promql.eval.VectorData.SeriesWindow;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.nodes.FunctionExpr;
import org.okapi.promql.eval.nodes.InstantizeExpr;
import org.okapi.promql.eval.nodes.SelectorExpr;
import org.okapi.promql.eval.ts.RESOLUTION;
import org.okapi.promql.parse.LabelMatcher;
import org.okapi.promql.parse.LabelOp;

public final class InfoFuncEval implements Evaluable {
  private static final long DEFAULT_STALENESS_MS = 5 * 60_000L;
  private static final List<String> IDENTIFYING_LABELS = List.of("instance", "job");

  private final FunctionExpr fn;

  public InfoFuncEval(FunctionExpr fn) {
    this.fn = fn;
  }

  @Override
  public ExpressionResult eval(EvalContext ctx) throws EvaluationException {
    if (fn.args.isEmpty() || fn.args.size() > 2) {
      throw new IllegalArgumentException("info(vector, [selector]) expects 1 or 2 args");
    }
    LogicalExpr baseExpr = fn.args.get(0);
    SelectorExpr infoSelector = null;
    if (fn.args.size() == 2) {
      infoSelector = unwrapSelector(fn.args.get(1));
    }

    boolean dropIfNoInfo = infoSelector != null && requiresNonEmptyLabels(infoSelector.matchers);
    InfoInclude include = computeInclude(infoSelector);

    if (ctx.startMs == ctx.endMs) {
      InstantVectorResult base = evalInstant(baseExpr, ctx);
      List<SeriesSample> out =
          joinAt(base, infoSelector, dropIfNoInfo, include, ctx, ctx.endMs, ctx.endMs);
      return new InstantVectorResult(out);
    }

    Map<SeriesId, List<Sample>> outMap = new LinkedHashMap<>();
    for (long t = ctx.startMs; t <= ctx.endMs; t += ctx.stepMs) {
      EvalContext stepCtx =
          new EvalContext(
              t,
              t,
              ctx.stepMs,
              ctx.resolution,
              ctx.client,
              ctx.discovery,
              ctx.exec,
              ctx.metricTypeResolver);
      InstantVectorResult base = evalInstant(baseExpr, stepCtx);
      List<SeriesSample> step =
          joinAt(base, infoSelector, dropIfNoInfo, include, stepCtx, t, t);
      for (SeriesSample s : step) {
        outMap.computeIfAbsent(s.series(), k -> new ArrayList<>()).add(s.sample());
      }
    }

    List<SeriesWindow> windows = new ArrayList<>();
    for (var e : outMap.entrySet()) {
      List<Sample> samples = e.getValue();
      samples.sort(Comparator.comparingLong(Sample::ts));
      List<Long> ts = new ArrayList<>(samples.size());
      List<Float> vals = new ArrayList<>(samples.size());
      for (Sample s : samples) {
        ts.add(s.ts());
        vals.add(s.value());
      }
      GaugeScan gs =
          GaugeScan.builder()
              .universalPath("")
              .timestamps(ts)
              .values(vals)
              .build();
      windows.add(new SeriesWindow(e.getKey(), gs));
    }
    return new RangeVectorResult(windows);
  }

  private InstantVectorResult evalInstant(LogicalExpr expr, EvalContext ctx)
      throws EvaluationException {
    var res = expr.lower().eval(ctx);
    if (res instanceof InstantVectorResult iv) return iv;
    if (res instanceof ScalarResult) {
      throw new IllegalArgumentException("info expects vector as first arg");
    }
    throw new IllegalArgumentException("info expects instant vector");
  }

  private List<SeriesSample> joinAt(
      InstantVectorResult base,
      SelectorExpr infoSelector,
      boolean dropIfNoInfo,
      InfoInclude include,
      EvalContext ctx,
      long evalTimeMs,
      long sampleTsMs)
      throws EvaluationException {
    List<SeriesSample> out = new ArrayList<>();
    List<InfoSeries> infoSeries = resolveInfoSeries(infoSelector, ctx, evalTimeMs);

    for (SeriesSample baseSample : base.data()) {
      SeriesId baseId = baseSample.series();
      if (isInfoMetric(baseId.metric())) {
        out.add(new SeriesSample(baseId, new Sample(sampleTsMs, baseSample.sample().value())));
        continue;
      }

      Map<String, List<Map<String, String>>> byMetric = new LinkedHashMap<>();
      for (InfoSeries info : infoSeries) {
        if (!identifyMatch(baseId.labels().tags(), info.labels)) continue;
        Map<String, String> infoLabels =
            infoDataLabels(baseId, info.labels, include.includeAll, include.labels);
        byMetric.computeIfAbsent(info.metric, k -> new ArrayList<>()).add(infoLabels);
      }

      if (byMetric.isEmpty()) {
        if (!dropIfNoInfo && (infoSelector == null || infoSeries.isEmpty())) {
          out.add(new SeriesSample(baseId, new Sample(sampleTsMs, baseSample.sample().value())));
        }
      } else {
        List<Map<String, String>> combined = List.of(new HashMap<>());
        for (var entry : byMetric.entrySet()) {
          List<Map<String, String>> next = new ArrayList<>();
          for (Map<String, String> baseMap : combined) {
            for (Map<String, String> infoMap : entry.getValue()) {
              Map<String, String> merged = new HashMap<>(baseMap);
              boolean conflict = false;
              for (var e : infoMap.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                if (merged.containsKey(key) && !merged.get(key).equals(val)) {
                  conflict = true;
                  break;
                }
                merged.put(key, val);
              }
              if (!conflict) {
                next.add(merged);
              }
            }
          }
          combined = next;
        }

        for (Map<String, String> labels : combined) {
          if (!include.includeAll && !include.labels.isEmpty()) {
            labels.keySet().retainAll(include.labels);
          }
          Map<String, String> outLabels = new HashMap<>(baseId.labels().tags());
          outLabels.putAll(labels);
          SeriesId outId = new SeriesId(baseId.metric(), new Labels(outLabels));
          out.add(new SeriesSample(outId, new Sample(sampleTsMs, baseSample.sample().value())));
        }
      }
    }
    return out;
  }

  private List<InfoSeries> resolveInfoSeries(
      SelectorExpr infoSelector, EvalContext ctx, long evalTimeMs) throws EvaluationException {
    String metricOrNull = null;
    List<LabelMatcher> matchers = List.of();
    if (infoSelector != null) {
      metricOrNull = infoSelector.metricOrNull;
      matchers = infoSelector.matchers == null ? List.of() : infoSelector.matchers;
      if (!hasNameMatcher(matchers) && metricOrNull == null) {
        matchers = withTargetInfo(matchers);
      }
    } else {
      matchers = List.of(new LabelMatcher("__name__", LabelOp.EQ, "target_info"));
    }

    long start = evalTimeMs - DEFAULT_STALENESS_MS;
    long end = evalTimeMs;
    List<SeriesId> series = ctx.discovery.expand(metricOrNull, matchers, start, end);
    List<InfoSeries> candidates = new ArrayList<>();
    for (SeriesId id : series) {
      Scan scan = ctx.client.get(id.metric(), id.labels().tags(), RESOLUTION.SECONDLY, start, end);
      if (!(scan instanceof GaugeScan gs)) {
        throw new EvaluationException("info series must be float type");
      }
      LastSample last = lastSample(gs, evalTimeMs, start);
      if (last == null) continue;
      candidates.add(new InfoSeries(id.metric(), id.labels().tags(), last.tsMs));
    }

    Map<IdentifyKey, Long> latestByKey = new HashMap<>();
    for (InfoSeries info : candidates) {
      IdentifyKey key = identifyKey(info.labels);
      long ts = info.lastTsMs;
      Long prev = latestByKey.get(key);
      if (prev == null || ts > prev) {
        latestByKey.put(key, ts);
      }
    }

    List<InfoSeries> out = new ArrayList<>();
    for (InfoSeries info : candidates) {
      IdentifyKey key = identifyKey(info.labels);
      Long max = latestByKey.get(key);
      if (max != null && info.lastTsMs == max) {
        out.add(info);
      }
    }
    return out;
  }

  private LastSample lastSample(GaugeScan gs, long evalTimeMs, long start) {
    List<Long> ts = gs.getTimestamps();
    List<Float> vals = gs.getValues();
    Float value = null;
    long lastTs = -1L;
    for (int i = 0; i < ts.size(); i++) {
      long t = ts.get(i);
      if (t <= start || t > evalTimeMs) continue;
      value = vals.get(i);
      lastTs = t;
    }
    if (value != null && Float.isNaN(value)) {
      return null;
    }
    if (value == null) return null;
    return new LastSample(lastTs, value);
  }

  private boolean identifyMatch(Map<String, String> base, Map<String, String> info) {
    for (String label : IDENTIFYING_LABELS) {
      String baseVal = base.get(label);
      String infoVal = info.get(label);
      if (baseVal == null || infoVal == null || !baseVal.equals(infoVal)) {
        return false;
      }
    }
    return true;
  }

  private Map<String, String> infoDataLabels(
      SeriesId base, Map<String, String> info, boolean includeAll, List<String> includeLabels) {
    Map<String, String> out = new HashMap<>();
    for (var e : info.entrySet()) {
      String k = e.getKey();
      if (k.equals("__name__")) continue;
      if (IDENTIFYING_LABELS.contains(k)) continue;
      if (base.labels().tags().containsKey(k)) continue;
      if (!includeAll && !includeLabels.contains(k)) continue;
      out.put(k, e.getValue());
    }
    return out;
  }

  private boolean isInfoMetric(String metric) {
    return metric != null && metric.endsWith("_info");
  }

  private boolean requiresNonEmptyLabels(List<LabelMatcher> matchers) {
    for (LabelMatcher m : matchers) {
      if ("__name__".equals(m.name())) continue;
      if (!acceptsEmpty(m)) return true;
    }
    return false;
  }

  private boolean acceptsEmpty(LabelMatcher m) {
    return switch (m.op()) {
      case EQ -> "".equals(m.value());
      case NE -> true;
      case RE -> Pattern.compile(m.value()).matcher("").matches();
      case NRE -> true;
    };
  }

  private SelectorExpr unwrapSelector(LogicalExpr expr) {
    if (expr instanceof InstantizeExpr ie && ie.inner instanceof SelectorExpr se) {
      return se;
    }
    if (expr instanceof SelectorExpr se) {
      return se;
    }
    throw new IllegalArgumentException("info selector must be a vector selector");
  }

  private boolean hasNameMatcher(List<LabelMatcher> matchers) {
    for (LabelMatcher m : matchers) {
      if ("__name__".equals(m.name())) return true;
    }
    return false;
  }

  private List<LabelMatcher> withTargetInfo(List<LabelMatcher> matchers) {
    List<LabelMatcher> out = new ArrayList<>(matchers);
    out.add(new LabelMatcher("__name__", LabelOp.EQ, "target_info"));
    return out;
  }

  private InfoInclude computeInclude(SelectorExpr selector) {
    if (selector == null) {
      return new InfoInclude(true, List.of());
    }
    List<LabelMatcher> matchers = selector.matchers == null ? List.of() : selector.matchers;
    List<String> includeLabels = new ArrayList<>();
    for (LabelMatcher m : matchers) {
      if ("__name__".equals(m.name())) continue;
      if (IDENTIFYING_LABELS.contains(m.name())) continue;
      includeLabels.add(m.name());
    }
    if (!includeLabels.isEmpty()) {
      return new InfoInclude(false, includeLabels);
    }
    boolean includeAll = selector.metricOrNull != null || hasNameMatcher(matchers);
    return new InfoInclude(includeAll, List.of());
  }

  private record InfoInclude(boolean includeAll, List<String> labels) {}

  private IdentifyKey identifyKey(Map<String, String> labels) {
    String instance = labels.get("instance");
    String job = labels.get("job");
    return new IdentifyKey(instance, job);
  }

  private record IdentifyKey(String instance, String job) {}

  private record LastSample(long tsMs, float value) {}

  private record InfoSeries(String metric, Map<String, String> labels, long lastTsMs) {}
}
