/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import com.clickhouse.client.api.Client;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.okapi.engine.api.TracesEngine;
import org.okapi.rest.common.UnionValue;
import org.okapi.rest.traces.*;
import org.okapi.rest.traces.red.ListServicesRequest;
import org.okapi.rest.traces.red.ServiceListResponse;
import org.okapi.rest.traces.red.ServiceRedRequest;
import org.okapi.rest.traces.red.ServiceRedResponse;
import org.okapi.spring.configs.Profiles;
import org.okapi.traces.ch.*;
import org.okapi.traces.ch.reds.ChRedQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile(Profiles.PROFILE_CH)
@RequiredArgsConstructor

// todo: write the impedance checking code
// todo: generate or write Monarch type files
// todo: get rid of flamegraphService
// todo: add reds as a grammar instead

public class ChTracesEngine implements TracesEngine {
  private final ChTraceQueryService queryService;
  private final ChSpanStatsQueryService statsQueryService;
  private final ChRedQueryService redQueryService;
  private final ChSpanAttributeHintsService hintsService;
  private final ChSpansFlameGraphService flameGraphService;
  private final ChTracesIngester ingester;
  private final ChTraceQlTranslator chTraceQlTranslator;
  private final ChTraceQlParser parser;
  private final ChQueryGenerator gen;
  private final ChGenericRecordUnionValueConverter recordConverter;
  private final Client client;

  @Override
  public SpanQueryV2Response query(SpanQueryV2Request request) {
    return queryService.getSpans(request);
  }

  @Override
  public OkapiTraceQlResponse queryWithTraceQl(OkapiTraceQlRequest request) {
    var exprNode = parser.parseExpression(request.getTraceQl());
    var translated =
        chTraceQlTranslator.translateTraceQl(
            exprNode, request.getTsStartNanos(), request.getTsEndNanos());
    var query = StringUtils.normalizeSpace(gen.generate(translated));
    var records = client.queryAll(query);
    var rows = new ArrayList<Map<String, UnionValue>>(records.size());
    for (var record : records) {
      rows.add(recordConverter.convert(record, translated.getSelectItems()));
    }
    return OkapiTraceQlResponse.builder().kind(translated.getTraceResultKind()).rows(rows).build();
  }

  @Override
  public SpanQueryV2SummaryResponse summary(SpanQueryV2Request request) {
    return queryService.getSpansSummary(request);
  }

  @Override
  public SpansQueryStatsResponse stats(SpansQueryStatsRequest request) {
    return statsQueryService.getStats(request);
  }

  @Override
  public ServiceRedResponse reds(ServiceRedRequest request) {
    return redQueryService.queryRed(request);
  }

  @Override
  public ServiceListResponse services(ListServicesRequest request) {
    return redQueryService.queryServiceList(request);
  }

  @Override
  public SpanAttributeHintsResponse attributeHints(SpanAttributeHintsRequest request) {
    return hintsService.getAttributeHints(request);
  }

  @Override
  public SpanAttributeValueHintsResponse attributeValueHints(
      SpanAttributeValueHintsRequest request) {
    return hintsService.getAttributeValueHints(request);
  }

  @Override
  public SpansFlameGraphResponse flameGraph(SpanQueryV2Request request) {
    return flameGraphService.queryFlameGraph(request);
  }

  @Override
  public void ingest(ExportTraceServiceRequest request) throws Exception {
    ingester.ingest(request);
  }
}
