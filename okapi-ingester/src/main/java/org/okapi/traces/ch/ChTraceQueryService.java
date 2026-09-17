/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.exceptions.DataFailureException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.traces.*;
import org.okapi.spring.configs.Profiles;
import org.okapi.timeutils.TimeUtils;
import org.okapi.traces.ch.template.ChTraceTemplateEngine;
import org.okapi.validation.OkapiChecks;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile(Profiles.PROFILE_CH)
public class ChTraceQueryService {
  private final Client client;
  private final ChTraceTemplateEngine templateEngine;
  private static final long OPERATION_TIMEOUT_MS = 1000;

  public ChTraceQueryService(Client client, ChTraceTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public SpanQueryV2Response getSpans(SpanQueryV2Request requestV2) {
    var template = buildTemplate(requestV2);
    var query =
        StringUtils.normalizeSpace(templateEngine.render(ChTemplateFiles.GET_SPANS_V2, template));
    var records = client.queryAll(query);
    var rows = new ArrayList<SpanRowV2>(records.size());
    for (var record : records) {
      rows.add(toRow(record));
    }
    return SpanQueryV2Response.builder().items(rows).build();
  }

  public SpanQueryV2SummaryResponse getSpansSummary(SpanQueryV2Request requestV2) {
    var template = buildTemplate(requestV2);
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_SUMMARY_V2, template);
    var records = client.queryAll(StringUtils.normalizeSpace(query));
    OkapiChecks.checkArgument(!records.isEmpty(), DataFailureException::new);
    var count = records.getFirst().getLong("result_count");
    return SpanQueryV2SummaryResponse.builder().count(count).build();
  }

  private ChSpansQueryTemplate buildTemplate(SpanQueryV2Request requestV2) {
    var http = requestV2.getHttpFilters();
    var db = requestV2.getDbFilters();
    var service = requestV2.getServiceFilter();
    var ts = requestV2.getTimestampFilter();
    var duration = requestV2.getDurationFilter();
    var builder =
        ChSpansQueryTemplate.builder()
            .table(ChConstants.TBL_SPANS_V1)
            .traceId(requestV2.getTraceId())
            .kind(requestV2.getKind());
    if (service != null) {
      builder.serviceName(service.getService()).servicePeerName(service.getPeer());
    }
    if (http != null) {
      builder
          .httpMethod(http.getHttpMethod())
          .httpStatusCode(http.getStatusCode())
          .httpOrigin(http.getOrigin())
          .httpHost(http.getHost());
    }
    if (db != null) {
      builder
          .dbSystem(db.getSystem())
          .dbCollection(db.getCollection())
          .dbNamespace(db.getNamespace())
          .dbOperation(db.getOperation());
    }
    if (ts != null) {
      builder.tsStartNs(ts.getTsStartNanos()).tsEndNs(ts.getTsEndNanos());
    }
    if (duration != null) {
      builder
          .durMinNs(TimeUtils.millisToNanos(duration.getDurMinMillis()))
          .durMaxNs(TimeUtils.millisToNanos(duration.getDurMaxMillis()));
    }
    if (requestV2.getSpanId() != null) {
      builder.spanId(requestV2.getSpanId());
    }
    builder
        .stringAttributeFilters(buildStringAttributeFilters(requestV2.getStringAttributesFilter()))
        .numberAttributeFilters(buildNumberAttributeFilters(requestV2.getNumberAttributesFilter()));
    return builder.limit(ChConstants.TRACE_QUERY_LIMIT).build();
  }

  private SpanRowV2 toRow(GenericRecord record) {
    var customStringAttributes = collectStringAttributes(record);
    var customNumberAttributes = collectNumberAttributes(record);
    return SpanRowV2.builder()
        .tsStartNs(record.getLong("ts_start_ns"))
        .tsEndNs(record.getLong("ts_end_ns"))
        .traceId(record.getString("trace_id"))
        .spanId(record.getString("span_id"))
        .spanStatus(getSpanStatus(record))
        .parentSpanId(record.getString("parent_span_id"))
        .kind(record.getString("kind"))
        .kindString(record.getString("kind_string"))
        .serviceName(record.getString("service_name"))
        .servicePeerName(record.getString("service_peer_name"))
        .httpMethod(record.getString("http_method"))
        .httpStatusCode(getInt(record, "http_status_code"))
        .httpRequestSize(getInt(record, "http_request_size"))
        .httpResponseSize(getInt(record, "http_response_size"))
        .httpOrigin(record.getString("http_origin"))
        .httpHost(record.getString("http_host"))
        .serverAddress(getInt(record, "server_address"))
        .serverPort(getInt(record, "server_port"))
        .clientAddress(record.getString("client_address"))
        .clientPort(getInt(record, "client_port"))
        .sourceAddress(record.getString("source_address"))
        .sourcePort(getInt(record, "source_port"))
        .networkProtocolType(record.getString("network_protocol_type"))
        .networkProtocolVersion(record.getString("network_protocol_version"))
        .dbSystemName(record.getString("db_system_name"))
        .dbCollectionName(record.getString("db_collection_name"))
        .dbNamespace(record.getString("db_namespace"))
        .dbOperationName(record.getString("db_operation_name"))
        .dbResponseStatusCode(getInt(record, "db_response_status_code"))
        .dbQueryText(record.getString("db_query_text"))
        .dbQuerySummary(record.getString("db_query_summary"))
        .dbStoredProcedureName(record.getString("db_stored_procedure_name"))
        .dbResponseReturnedRows(getInt(record, "db_response_returned_rows"))
        .rpcMethod(record.getString("rpc_method"))
        .rpcMethodOriginal(record.getString("rpc_method_original"))
        .rpcResponseStatusCode(getInt(record, "rpc_response_status_code"))
        .customStringAttributes(customStringAttributes)
        .customNumberAttributes(customNumberAttributes)
        .build();
  }

  private static org.okapi.rest.traces.SpanStatus getSpanStatus(GenericRecord record) {
    var value = record.getString("span_status");
    if (value == null || value.isEmpty()) {
      return org.okapi.rest.traces.SpanStatus.UNK;
    }
    try {
      return org.okapi.rest.traces.SpanStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      return org.okapi.rest.traces.SpanStatus.UNK;
    }
  }

  private static Integer getInt(GenericRecord record, String key) {
    try {
      return (int) record.getLong(key);
    } catch (Exception e) {
      return null;
    }
  }

  private static Map<String, String> collectStringAttributes(GenericRecord record) {
    Map<String, String> out = new HashMap<>();
    for (int i = 0; i < ChSpanAttributeBucketer.BUCKETS; i++) {
      @SuppressWarnings("unchecked")
      var bucket = (Map<String, String>) record.getObject("attribs_str_" + i);
      if (bucket != null && !bucket.isEmpty()) {
        out.putAll(bucket);
      }
    }
    return out.isEmpty() ? null : out;
  }

  private static Map<String, Double> collectNumberAttributes(GenericRecord record) {
    Map<String, Double> out = new HashMap<>();
    for (int i = 0; i < ChSpanAttributeBucketer.BUCKETS; i++) {
      @SuppressWarnings("unchecked")
      var bucket = (Map<String, Object>) record.getObject("attribs_number_" + i);
      if (bucket == null || bucket.isEmpty()) continue;
      for (var entry : bucket.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof Number number) {
          out.put(entry.getKey(), number.doubleValue());
        }
      }
    }
    return out.isEmpty() ? null : out;
  }

  private static List<ChSpanStringAttributeFilter> buildStringAttributeFilters(
      List<StringAttributeFilter> filters) {
    if (filters == null || filters.isEmpty()) return List.of();
    var out = new ArrayList<ChSpanStringAttributeFilter>();
    for (var filter : filters) {
      if (filter == null) continue;
      var key = filter.getKey();
      var value = filter.getValue();
      if (key == null || key.isEmpty() || value == null) continue;
      out.add(
          ChSpanStringAttributeFilter.builder()
              .key(key)
              .bucket(ChSpanAttributeBucketer.bucketForKey(key))
              .value(value)
              .build());
    }
    return out;
  }

  private static List<ChSpanNumberAttributeFilter> buildNumberAttributeFilters(
      List<NumberAttributeFilter> filters) {
    if (filters == null || filters.isEmpty()) return List.of();
    var out = new ArrayList<ChSpanNumberAttributeFilter>();
    for (var filter : filters) {
      if (filter == null) continue;
      var key = filter.getKey();
      var value = filter.getValue();
      if (key == null || key.isEmpty() || value == null) continue;
      out.add(
          ChSpanNumberAttributeFilter.builder()
              .key(key)
              .bucket(ChSpanAttributeBucketer.bucketForKey(key))
              .value(value)
              .build());
    }
    return out;
  }
}
