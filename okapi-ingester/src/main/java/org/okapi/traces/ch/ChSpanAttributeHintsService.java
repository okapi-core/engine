/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.okapi.metrics.ch.ChConstants.TBL_SPANS_V1;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.rest.traces.NumericAttributeSummary;
import org.okapi.rest.traces.SpanAttributeHint;
import org.okapi.rest.traces.SpanAttributeHintsRequest;
import org.okapi.rest.traces.SpanAttributeHintsResponse;
import org.okapi.rest.traces.SpanAttributeValueHintsRequest;
import org.okapi.rest.traces.SpanAttributeValueHintsResponse;
import org.okapi.rest.traces.TimestampMillisFilter;
import org.okapi.spring.configs.Profiles;
import org.okapi.traces.ch.template.ChTraceTemplateEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile(Profiles.PROFILE_CH)
public class ChSpanAttributeHintsService {
  private static final int VALUE_HINTS_LIMIT = 100;
  private final Client client;
  private final ChTraceTemplateEngine templateEngine;

  public ChSpanAttributeHintsService(Client client, ChTraceTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public SpanAttributeHintsResponse getAttributeHints(SpanAttributeHintsRequest request) {
    var template = buildCustomHintsTemplate(request);
    var query = templateEngine.render(ChTemplateFiles.GET_SPAN_ATTRIBUTE_HINTS_CUSTOM, template);
    var records = client.queryAll(query);
    var custom = new ArrayList<SpanAttributeHint>(records.size());
    for (var record : records) {
      var name = record.getString("attribute_name");
      var type = record.getString("attribute_type");
      if (name != null && !name.isEmpty()) {
        custom.add(
            SpanAttributeHint.builder()
                .name(name)
                .type(type == null || type.isEmpty() ? "string" : type)
                .build());
      }
    }
    var defaults = new ArrayList<SpanAttributeHint>();
    for (var name : ChSpanAtttributes.getDefaultAttributes()) {
      defaults.add(
          SpanAttributeHint.builder().name(name).type(ChSpanAtttributes.typeOf(name)).build());
    }
    return SpanAttributeHintsResponse.builder()
        .defaultAttributes(defaults)
        .customAttributes(custom)
        .build();
  }

  public SpanAttributeValueHintsResponse getAttributeValueHints(
      SpanAttributeValueHintsRequest request) {
    if (request == null
        || request.getAttributeName() == null
        || request.getAttributeName().isEmpty()) {
      return SpanAttributeValueHintsResponse.builder()
          .attributeName(request == null ? null : request.getAttributeName())
          .values(List.of())
          .build();
    }
    String attr = request.getAttributeName();
    if (ChSpanAtttributes.isDefault(attr)) {
      return getDefaultAttributeValueHints(request);
    }
    return getCustomAttributeValueHints(request);
  }

  private SpanAttributeValueHintsResponse getCustomAttributeValueHints(
      SpanAttributeValueHintsRequest request) {
    var attr = request.getAttributeName();
    var type = getAttributeType(attr);
    if ("number".equals(type)) {
      var template = buildCustomNumericTemplate(request);
      var query =
          templateEngine.render(ChTemplateFiles.GET_SPAN_ATTRIBUTE_VALUES_CUSTOM_NUM, template);
      var records = client.queryAll(query);
      NumericAttributeSummary summary = null;
      if (!records.isEmpty()) {
        var record = records.getFirst();
        summary =
            NumericAttributeSummary.builder()
                .avg(record.getDouble("avg"))
                .p25(record.getDouble("p25"))
                .p50(record.getDouble("p50"))
                .p75(record.getDouble("p75"))
                .p90(record.getDouble("p90"))
                .build();
      }
      return SpanAttributeValueHintsResponse.builder()
          .attributeName(attr)
          .numericSummary(summary)
          .values(List.of())
          .build();
    }
    var template = buildCustomStringTemplate(request);
    var query =
        templateEngine.render(ChTemplateFiles.GET_SPAN_ATTRIBUTE_VALUES_CUSTOM_STR, template);
    var records = client.queryAll(query);
    var values = new ArrayList<String>();
    for (GenericRecord record : records) {
      var v = record.getString("value");
      if (v != null) values.add(v);
    }
    return SpanAttributeValueHintsResponse.builder()
        .attributeName(attr)
        .numericSummary(null)
        .values(values)
        .build();
  }

  private SpanAttributeValueHintsResponse getDefaultAttributeValueHints(
      SpanAttributeValueHintsRequest request) {
    var attr = request.getAttributeName();
    if (ChSpanAtttributes.isNumeric(attr)) {
      var template = buildDefaultNumericTemplate(request);
      var query =
          templateEngine.render(ChTemplateFiles.GET_SPAN_ATTRIBUTE_VALUES_DEFAULT_NUM, template);
      var records = client.queryAll(query);
      NumericAttributeSummary summary = null;
      if (!records.isEmpty()) {
        var record = records.getFirst();
        // add null checks here.
        var builder = NumericAttributeSummary.builder();
        if (record.hasValue("avg")) {
          builder.avg(record.getDouble("avg"));
        }
        if (record.hasValue("p25")) {
          builder.p25(record.getDouble("p25"));
        }
        if (record.hasValue("p50")) {
          builder.p50(record.getDouble("p50"));
        }
        if (record.hasValue("p75")) {
          builder.p75(record.getDouble("p75"));
        }
        if (record.hasValue("p90")) {
          builder.p90(record.getDouble("p90"));
        }
        summary = builder.build();
      }
      return SpanAttributeValueHintsResponse.builder()
          .attributeName(attr)
          .numericSummary(summary)
          .values(List.of())
          .build();
    }
    var template = buildDefaultStringTemplate(request);
    var query =
        templateEngine.render(ChTemplateFiles.GET_SPAN_ATTRIBUTE_VALUES_DEFAULT_STR, template);
    var records = client.queryAll(query);
    var values = new ArrayList<String>();
    for (GenericRecord record : records) {
      var v = record.getString("value");
      if (v != null) values.add(v);
    }
    return SpanAttributeValueHintsResponse.builder()
        .attributeName(attr)
        .numericSummary(null)
        .values(values)
        .build();
  }

  private String getAttributeType(String attr) {
    var template = ChSpansAttributeTypesTemplate.builder().attributes(List.of(attr)).build();
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_ATTRIBUTE_TYPES, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) return "string";
    var type = records.getFirst().getString("attribute_type");
    return type == null ? "string" : type;
  }

  private ChSpansAttributeHintsQueryTemplate buildCustomHintsTemplate(
      SpanAttributeHintsRequest request) {
    var ts = request == null ? null : request.getTimestampFilter();
    Long start = null;
    Long end = null;
    if (ts != null) {
      start = ts.getTsMillisStart() * 1_000_000L;
      end = ts.getTsMillisEnd() * 1_000_000L;
    }
    return ChSpansAttributeHintsQueryTemplate.builder()
        .table(ChConstants.TBL_SPANS_INGESTED_ATTRIBS)
        .tsStartNs(start)
        .tsEndNs(end)
        .limit(ChConstants.TRACE_HINTS_LIMITS)
        .build();
  }

  private ChSpanAttributeValuesCustomTemplate buildCustomStringTemplate(
      SpanAttributeValueHintsRequest request) {
    var attr = request.getAttributeName();
    int bucket = ChSpanAttributeBucketer.bucketForKey(attr);
    return ChSpanAttributeValuesCustomTemplate.builder()
        .table(TBL_SPANS_V1)
        .attributeName(attr)
        .bucket(bucket)
        .valueExpr("attribs_str_" + bucket + "['" + attr + "']")
        .limit(VALUE_HINTS_LIMIT)
        .tsStartNs(toStartNs(request.getTimestampFilter()))
        .tsEndNs(toEndNs(request.getTimestampFilter()))
        .build();
  }

  private ChSpanAttributeValuesCustomTemplate buildCustomNumericTemplate(
      SpanAttributeValueHintsRequest request) {
    var attr = request.getAttributeName();
    int bucket = ChSpanAttributeBucketer.bucketForKey(attr);
    return ChSpanAttributeValuesCustomTemplate.builder()
        .table(TBL_SPANS_V1)
        .attributeName(attr)
        .bucket(bucket)
        .valueExpr("attribs_number_" + bucket + "['" + attr + "']")
        .limit(VALUE_HINTS_LIMIT)
        .tsStartNs(toStartNs(request.getTimestampFilter()))
        .tsEndNs(toEndNs(request.getTimestampFilter()))
        .build();
  }

  private ChSpanAttributeValuesDefaultTemplate buildDefaultStringTemplate(
      SpanAttributeValueHintsRequest request) {
    return ChSpanAttributeValuesDefaultTemplate.builder()
        .table(ChConstants.TBL_SPANS_V1)
        .attributeName(request.getAttributeName())
        .limit(VALUE_HINTS_LIMIT)
        .tsStartNs(toStartNs(request.getTimestampFilter()))
        .tsEndNs(toEndNs(request.getTimestampFilter()))
        .build();
  }

  private ChSpanAttributeValuesDefaultTemplate buildDefaultNumericTemplate(
      SpanAttributeValueHintsRequest request) {
    return ChSpanAttributeValuesDefaultTemplate.builder()
        .table(ChConstants.TBL_SPANS_V1)
        .attributeName(request.getAttributeName())
        .limit(VALUE_HINTS_LIMIT)
        .tsStartNs(toStartNs(request.getTimestampFilter()))
        .tsEndNs(toEndNs(request.getTimestampFilter()))
        .build();
  }

  private Long toStartNs(TimestampMillisFilter filter) {
    if (filter == null) return null;
    return filter.getTsMillisStart() * 1_000_000L;
  }

  private Long toEndNs(TimestampMillisFilter filter) {
    if (filter == null) return null;
    return filter.getTsMillisEnd() * 1_000_000L;
  }
}
