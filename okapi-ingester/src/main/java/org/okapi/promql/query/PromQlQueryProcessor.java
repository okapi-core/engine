/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.query;

import java.util.*;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.okapi.exceptions.BadRequestException;
import org.okapi.promql.eval.ExpressionEvaluator;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.VectorData;
import org.okapi.promql.eval.exceptions.EvaluationException;
import org.okapi.promql.eval.ts.StatisticsMerger;
import org.okapi.promql.parser.PromQLLexer;
import org.okapi.promql.parser.PromQLParser;
import org.okapi.promql.runtime.SeriesDiscoveryFactory;
import org.okapi.promql.runtime.TsClientFactory;
import org.okapi.rest.promql.GetPromQlResponse;
import org.okapi.rest.promql.PromQlResponseMapper;
import org.okapi.rest.promql.PromQlStringListResponse;

@RequiredArgsConstructor
public class PromQlQueryProcessor {

  final ExecutorService exec;
  final StatisticsMerger merger;
  final TsClientFactory metricsClientFactory;
  final SeriesDiscoveryFactory seriesDiscoveryFactory;
  UrlUnEscaper urlUnEscaper = new UrlUnEscaper();

  public ExpressionResult queryRange(String promql, String start, String end, String step)
      throws EvaluationException, BadRequestException {
    return queryRange(
        promql,
        PromQlApiTimeParams.requiredTime(start, "start-date"),
        PromQlApiTimeParams.requiredTime(end, "end-date"),
        PromQlApiTimeParams.requiredStepMillis(step));
  }

  public ExpressionResult queryRange(String promql, long startMs, long endMs, long stepMs)
      throws EvaluationException, BadRequestException {
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var client = metricsClientFactory.getClient();
    if (client.isEmpty()) {
      throw new BadRequestException("Cluster is unavailable as we may be under maintenance.");
    }
    var discovery = seriesDiscoveryFactory.get();
    var parser = new PromQLParser(tokens);
    var evaluator = new ExpressionEvaluator(client.get(), discovery, exec, merger);
    return evaluator.evaluate(promql, startMs, endMs, stepMs, parser);
  }

  public ExpressionResult queryPointInTime(String promql, long instant)
      throws EvaluationException, BadRequestException {
    var lexer = new PromQLLexer(CharStreams.fromString(promql));
    var tokens = new CommonTokenStream(lexer);
    var client = metricsClientFactory.getClient();
    if (client.isEmpty()) {
      throw new BadRequestException("Cluster is unavailable as we may be resharding.");
    }
    var discovery = seriesDiscoveryFactory.get();
    var parser = new PromQLParser(tokens);
    var evaluator = new ExpressionEvaluator(client.get(), discovery, exec, merger);
    return evaluator.evaluateAt(promql, instant, parser);
  }

  public GetPromQlResponse queryRangeApi(String promQl, String start, String end, String step)
      throws BadRequestException, EvaluationException {
    var result = queryRange(promQl, start, end, step);
    return PromQlResponseMapper.toResult(result, PromQlResponseMapper.RETURN_TYPE.MATRIX);
  }

  public GetPromQlResponse queryInstantApi(String promQl, String time)
      throws BadRequestException, EvaluationException {
    var now = PromQlApiTimeParams.optionalTime(time, System.currentTimeMillis());
    var result = queryPointInTime(promQl, now);
    return PromQlResponseMapper.toResult(result, PromQlResponseMapper.RETURN_TYPE.VECTOR_OR_SCALAR);
  }

  public Set<VectorData.SeriesId> getMatches(List<String> conditions, long start, long end)
      throws BadRequestException {
    var allMatches = new HashSet<VectorData.SeriesId>();
    if (conditions == null || conditions.isEmpty()) {
      var discovery = seriesDiscoveryFactory.get();
      allMatches.addAll(discovery.expand(null, Collections.emptyList(), start, end));
      return Collections.unmodifiableSet(allMatches);
    }
    for (var match : conditions) {
      var lexer = new PromQLLexer(CharStreams.fromString(match));
      var tokens = new CommonTokenStream(lexer);
      var discovery = seriesDiscoveryFactory.get();
      var client = metricsClientFactory.getClient();
      if (client.isEmpty()) {
        throw new BadRequestException("Cluster is unavailable as we may be resharding.");
      }
      var parser = new PromQLParser(tokens);
      var evaluator = new ExpressionEvaluator(client.get(), discovery, exec, merger);
      var matchingSeries = evaluator.find(parser, start, end);
      allMatches.addAll(matchingSeries);
    }
    return Collections.unmodifiableSet(allMatches);
  }

  public PromQlStringListResponse queryLabelNamesApi(List<String> matches, String start, String end)
      throws BadRequestException {
    var conditions = matches != null ? matches : Collections.<String>emptyList();
    var range = PromQlApiTimeParams.optionalRange(start, end);
    var matchingSeriesIds = getMatches(conditions, range.getStartMs(), range.getEndMs());
    var labelNames = new HashSet<String>();
    for (var id : matchingSeriesIds) {
      labelNames.add(PromQlResponseMapper.NAME);
      if (id.labels() != null && id.labels().tags() != null) {
        labelNames.addAll(id.labels().tags().keySet());
      }
    }
    var list = new ArrayList<>(labelNames);
    Collections.sort(list);
    return PromQlResponseMapper.mapStringList(list);
  }

  public PromQlStringListResponse queryLabelsApi(
      String rawLabel, List<String> matches, String start, String end) throws BadRequestException {
    var range = PromQlApiTimeParams.optionalRange(start, end);
    var label = urlUnEscaper.unescape(rawLabel);
    var conditions = matches != null ? matches : Collections.<String>emptyList();
    var matchingSeriesIds = getMatches(conditions, range.getStartMs(), range.getEndMs());
    var labelValues = new HashSet<String>();
    if ("__name__".equals(label)) {
      for (var id : matchingSeriesIds) {
        if (id.metric() != null) {
          labelValues.add(id.metric());
        }
      }
    } else {
      for (var id : matchingSeriesIds) {
        if (id.labels() != null
            && id.labels().tags() != null
            && id.labels().tags().containsKey(label)) {
          labelValues.add(id.labels().tags().get(label));
        }
      }
    }
    return PromQlResponseMapper.mapStringList(new ArrayList<>(labelValues));
  }
}
