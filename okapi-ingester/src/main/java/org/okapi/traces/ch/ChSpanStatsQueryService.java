/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.query.GenericRecord;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.okapi.ch.ChTemplateFiles;
import org.okapi.collections.OkapiMaps;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.*;
import org.okapi.spring.configs.Profiles;
import org.okapi.traces.ch.template.ChTraceTemplateEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile(Profiles.PROFILE_CH)
public class ChSpanStatsQueryService {
  private final Client client;
  private final ChTraceTemplateEngine templateEngine;

  public ChSpanStatsQueryService(Client client, ChTraceTemplateEngine templateEngine) {
    this.client = client;
    this.templateEngine = templateEngine;
  }

  public SpansQueryStatsResponse getStats(SpansQueryStatsRequest request)
      throws BadRequestException {
    var attributes = removeBlanksAndGetUniques(request.getAttributes());
    if (attributes.isEmpty()) {
      return SpansQueryStatsResponse.builder()
          .count(countMatches(request))
          .numericSeries(List.of())
          .distributionSummaries(List.of())
          .build();
    }
    var typeMap = getAttributeTypes(attributes);
    validateRequest(request, attributes, typeMap);
    var builder = SpansQueryStatsResponse.builder();
    long count = countMatches(request);
    builder.count(count);

    var numericAttributes =
        attributes.stream()
            .filter(attr -> ChAttributeTypes.CH_NUMERIC.equals(typeMap.get(attr)))
            .toList();
    var categoricalAttributes =
        attributes.stream()
            .filter(attr -> ChAttributeTypes.CH_STRING.equals(typeMap.get(attr)))
            .toList();
    if (!numericAttributes.isEmpty()) {
      var numericSeries = queryNumericSummaries(request, numericAttributes);
      builder.numericSeries(numericSeries);
    }
    if (!categoricalAttributes.isEmpty()) {
      var summaries = queryCategoricalSummaries(request, categoricalAttributes);
      builder.distributionSummaries(summaries);
    }
    return builder.build();
  }

  public AttributeNumericSeries queryDefaultNumericAttribute(
      String attribute, AGG_TYPE aggregation, RES_TYPE resolution, String whereClause) {
    var bucketStartExpr = ChSpanStatsQueryBuilder.buildBucketStartExpr(resolution);
    var aggClause = ChSpanStatsQueryBuilder.buildAggClause(aggregation, attribute);
    var presenceClause = "isNotNull(" + attribute + ")";
    return queryNumericSummary(
        attribute,
        bucketStartExpr,
        aggClause,
        presenceClause,
        whereClause,
        ChTemplateFiles.GET_SPAN_DEFAULT_NUMERIC_ATTRIBUTE_SUMMARY);
  }

  public AttributeNumericSeries queryCustomNumericAttribute(
      String attribute, AGG_TYPE aggregation, RES_TYPE resolution, String whereClause) {
    var bucketStartExpr = ChSpanStatsQueryBuilder.buildBucketStartExpr(resolution);
    var aggClause = ChSpanStatsQueryBuilder.buildAggClause(aggregation, attribute);
    var bucket = ChSpanAttributeBucketer.bucketForKey(attribute);
    var presenceClause = "mapContains(attribs_number_" + bucket + ", '" + attribute + "')";
    return queryNumericSummary(
        attribute,
        bucketStartExpr,
        aggClause,
        presenceClause,
        whereClause,
        ChTemplateFiles.GET_SPAN_CUSTOM_NUMERIC_ATTRIBUTE_SUMMARY);
  }

  private AttributeNumericSeries queryNumericSummary(
      String attribute,
      String bucketStartExpr,
      String aggClause,
      String presenceClause,
      String whereClause,
      String templateName) {
    var template =
        ChSpanNumericalAttributeSummaryTemplate.builder()
            .table(ChConstants.TBL_SPANS_V1)
            .attribute(attribute)
            .bucketStartExpr(bucketStartExpr)
            .aggClause(aggClause)
            .presenceClause(presenceClause)
            .whereClause(whereClause)
            .build();
    var query = templateEngine.render(templateName, template);
    var records = client.queryAll(query);
    var points = new ArrayList<NumericPoint>();
    for (GenericRecord record : records) {
      var bucketMs = record.getLong("bucket_start_ms");
      var value = record.getDouble("value");
      points.add(NumericPoint.builder().bucketStartMs(bucketMs).value(value).build());
    }
    return AttributeNumericSeries.builder().attribute(attribute).points(points).build();
  }

  private long countMatches(SpansQueryStatsRequest request) {
    var template = buildCountTemplate(request);
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_STATS_COUNT, template);
    var records = client.queryAll(query);
    if (records.isEmpty()) return 0L;
    return records.getFirst().getLong("cnt");
  }

  private List<AttributeNumericSeries> queryNumericSummaries(
      SpansQueryStatsRequest request, List<String> attributes) {
    if (attributes.isEmpty()) return List.of();
    var numericalAgg = request.getNumericalAgg();
    var whereClause = buildWhereClause(request);
    var series = new ArrayList<AttributeNumericSeries>();
    for (var attribute : attributes) {
      if (ChSpanAtttributes.isDefault(attribute)) {
        var summary =
            queryDefaultNumericAttribute(
                attribute, numericalAgg.getAggregation(), numericalAgg.getResType(), whereClause);
        series.add(summary);
      } else {
        var summary =
            queryCustomNumericAttribute(
                attribute, numericalAgg.getAggregation(), numericalAgg.getResType(), whereClause);
        series.add(summary);
      }
    }
    return series;
  }

  private List<AttributeDistributionSummary> queryCategoricalSummaries(
      SpansQueryStatsRequest request, List<String> attributes) {
    boolean approximate =
        request.getSummaryConfig() != null && request.getSummaryConfig().isApproximateCount();
    var template = buildDistributionTemplate(request, attributes, approximate);
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_STATS_DISTRIBUTION, template);
    var records = client.queryAll(query);
    Map<String, List<ValueCount>> valuesByAttr = new HashMap<>();
    for (GenericRecord record : records) {
      var attr = record.getString("attribute_name");
      var value = record.getString("attribute_value");
      var count = record.getLong("count");
      valuesByAttr
          .computeIfAbsent(attr, k -> new ArrayList<>())
          .add(ValueCount.builder().value(value).count(count).build());
    }
    var summaries = new ArrayList<AttributeDistributionSummary>();
    for (var entry : valuesByAttr.entrySet()) {
      summaries.add(
          AttributeDistributionSummary.builder()
              .attribute(entry.getKey())
              .values(entry.getValue())
              .build());
    }
    return summaries;
  }

  private void validateRequest(
      SpansQueryStatsRequest request, Collection<String> attributes, Map<String, String> typeMap)
      throws BadRequestException {
    var numericalAgg = request.getNumericalAgg();
    if (numericalAgg == null || numericalAgg.getAggregation() == null) return;
    if (numericalAgg.getAggregation() == AGG_TYPE.COUNT) return;
    if (numericalAgg.getResType() == null) {
      throw new BadRequestException("Resolution is required for numerical aggregations");
    }
    for (var attr : attributes) {
      var type = typeMap.get(attr);
      if (!"number".equals(type)) {
        throw new BadRequestException(
            "Numerical aggregation is not supported for non-numerical attribute: " + attr);
      }
    }
  }

  private Map<String, String> getAttributeTypes(List<String> attributes) {
    var defaultAttribs =
        attributes.stream().filter(ChSpanAtttributes::isDefault).collect(Collectors.toSet());
    var notDefault = attributes.stream().filter(attr -> !defaultAttribs.contains(attr)).toList();
    Map<String, String> defaultMap = new HashMap<>();
    for (var attr : defaultAttribs) {
      if (ChSpanAtttributes.isNumeric(attr)) {
        defaultMap.put(attr, ChAttributeTypes.CH_NUMERIC);
      } else {
        defaultMap.put(attr, ChAttributeTypes.CH_STRING);
      }
    }
    var nonDefaultTypes = getCustomAttributeTypes(notDefault);
    return OkapiMaps.mergeMaps(defaultMap, nonDefaultTypes, (A, B, C) -> "mixed", HashMap::new);
  }

  private Map<String, String> getCustomAttributeTypes(List<String> attributes) {
    if (attributes.isEmpty()) {
      return Collections.emptyMap();
    }
    var template = ChSpansAttributeTypesTemplate.builder().attributes(attributes).build();
    var query = templateEngine.render(ChTemplateFiles.GET_SPANS_ATTRIBUTE_TYPES, template);
    var records = client.queryAll(query);
    Map<String, String> out = new HashMap<>();
    for (GenericRecord record : records) {
      var name = record.getString("attribute_name");
      var type = record.getString("attribute_type");
      if (name == null || type == null) continue;
      var existing = out.get(name);
      if (existing == null) {
        out.put(name, type);
      } else if (!existing.equals(type)) {
        out.put(name, "mixed");
      }
    }
    return out;
  }

  private ChSpansStatsCountTemplate buildCountTemplate(SpansQueryStatsRequest request) {
    return ChSpansStatsCountTemplate.builder()
        .table(ChConstants.TBL_SPANS_V1)
        .traceId(request.getTraceId())
        .kind(request.getKind())
        .serviceFilter(request.getServiceFilter())
        .httpFilters(request.getHttpFilters())
        .dbFilters(request.getDbFilters())
        .timestampFilter(request.getTimestampFilter())
        .durationFilter(request.getDurationFilter())
        .stringFilters(buildStringFilters(request.getStringAttributesFilter()))
        .numberFilters(buildNumberFilters(request.getNumberAttributesFilter()))
        .build();
  }

  private String buildWhereClause(SpansQueryStatsRequest request) {
    var clauses = new ArrayList<String>();
    var colFilters = buildColFilters(request);
    for (var f : colFilters) {
      if (f.getStrValue() != null) {
        clauses.add(f.getColName() + " = '" + f.getStrValue() + "'");
      } else if (f.getIntValue() != null) {
        clauses.add(f.getColName() + " = " + f.getIntValue());
      } else if (f.getDoubleValue() != null) {
        clauses.add(f.getColName() + " = " + f.getDoubleValue());
      }
    }
    var rawClauses = getTimeRangeAndDurationClauses(request);
    if (!rawClauses.isEmpty()) {
      clauses.addAll(rawClauses);
    }
    if (clauses.isEmpty()) return null;
    return String.join(" AND ", clauses);
  }

  private ChSpansStatsDistributionTemplate buildDistributionTemplate(
      SpansQueryStatsRequest request, List<String> attributes, boolean approximate) {
    var templateBuilder =
        ChSpansStatsDistributionTemplate.builder()
            .table(ChConstants.TBL_SPANS_V1)
            .approximate(approximate)
            .limit(ChConstants.TRACE_HINTS_LIMITS)
            .attributes(attributes);
    if (request.getTraceId() != null) {
      templateBuilder.traceId(request.getTraceId());
    }
    if (request.getKind() != null) {
      templateBuilder.kind(request.getKind());
    }
    if (request.getServiceFilter() != null) {
      templateBuilder.serviceFilter(request.getServiceFilter());
    }
    if (request.getServiceFilter() != null) {
      templateBuilder.serviceFilter(request.getServiceFilter());
    }
    if (request.getHttpFilters() != null) {
      templateBuilder.httpFilters(request.getHttpFilters());
    }
    if (request.getDbFilters() != null) {
      templateBuilder.dbFilters(request.getDbFilters());
    }
    if (request.getTimestampFilter() != null) {
      templateBuilder.timestampFilter(request.getTimestampFilter());
    }
    if (request.getDurationFilter() != null) {
      templateBuilder.durationFilter(request.getDurationFilter());
    }
    if (request.getStringAttributesFilter() != null) {
      templateBuilder.stringFilters(buildStringFilters(request.getStringAttributesFilter()));
    }
    if (request.getNumberAttributesFilter() != null) {
      templateBuilder.numberFilters(buildNumberFilters(request.getNumberAttributesFilter()));
    }
    if (request.getDurationFilter() != null) {
      templateBuilder.durationFilter(request.getDurationFilter());
    }
    return templateBuilder.build();
  }

  private List<ChSpanStringAttributeFilter> buildStringFilters(
      List<StringAttributeFilter> filters) {
    if (filters == null) return Collections.emptyList();
    return filters.stream()
        .filter(Objects::nonNull)
        .map(
            filter -> {
              var key = filter.getKey();
              var value = filter.getValue();
              return ChSpanStringAttributeFilter.builder()
                  .key(key)
                  .bucket(ChSpanAttributeBucketer.bucketForKey(key))
                  .value(value)
                  .build();
            })
        .toList();
  }

  private List<ChSpanNumberAttributeFilter> buildNumberFilters(
      List<NumberAttributeFilter> filters) {
    if (filters == null) return Collections.emptyList();
    var out = new ArrayList<ChSpanNumberAttributeFilter>();
    for (var filter : filters) {
      var key = filter.getKey();
      var value = filter.getValue();
      out.add(
          ChSpanNumberAttributeFilter.builder()
              .key(key)
              .bucket(ChSpanAttributeBucketer.bucketForKey(key))
              .value(value)
              .build());
    }
    return out;
  }

  private List<String> removeBlanksAndGetUniques(List<String> attributes) {
    if (attributes == null) return Collections.emptyList();
    return attributes.stream().filter(a -> !a.isBlank()).distinct().toList();
  }

  private List<ColValueFilter> buildColFilters(SpansQueryStatsRequest request) {
    var out = new ArrayList<ColValueFilter>();
    if (request.getTraceId() != null && !request.getTraceId().isEmpty()) {
      out.add(ColValueFilter.builder().colName("trace_id").strValue(request.getTraceId()).build());
    }
    if (request.getKind() != null && !request.getKind().isEmpty()) {
      out.add(ColValueFilter.builder().colName("kind").strValue(request.getKind()).build());
    }
    var service = request.getServiceFilter();
    if (service != null) {
      if (service.getService() != null && !service.getService().isEmpty()) {
        out.add(
            ColValueFilter.builder()
                .colName("service_name")
                .strValue(service.getService())
                .build());
      }
      if (service.getPeer() != null && !service.getPeer().isEmpty()) {
        out.add(
            ColValueFilter.builder()
                .colName("service_peer_name")
                .strValue(service.getPeer())
                .build());
      }
    }
    var http = request.getHttpFilters();
    if (http != null) {
      if (http.getHttpMethod() != null && !http.getHttpMethod().isEmpty()) {
        out.add(
            ColValueFilter.builder().colName("http_method").strValue(http.getHttpMethod()).build());
      }
      if (http.getStatusCode() != null) {
        out.add(
            ColValueFilter.builder()
                .colName("http_status_code")
                .intValue(http.getStatusCode())
                .build());
      }
      if (http.getOrigin() != null && !http.getOrigin().isEmpty()) {
        out.add(ColValueFilter.builder().colName("http_origin").strValue(http.getOrigin()).build());
      }
      if (http.getHost() != null && !http.getHost().isEmpty()) {
        out.add(ColValueFilter.builder().colName("http_host").strValue(http.getHost()).build());
      }
    }
    var db = request.getDbFilters();
    if (db != null) {
      if (db.getSystem() != null && !db.getSystem().isEmpty()) {
        out.add(
            ColValueFilter.builder().colName("db_system_name").strValue(db.getSystem()).build());
      }
      if (db.getCollection() != null && !db.getCollection().isEmpty()) {
        out.add(
            ColValueFilter.builder()
                .colName("db_collection_name")
                .strValue(db.getCollection())
                .build());
      }
      if (db.getNamespace() != null && !db.getNamespace().isEmpty()) {
        out.add(
            ColValueFilter.builder().colName("db_namespace").strValue(db.getNamespace()).build());
      }
      if (db.getOperation() != null && !db.getOperation().isEmpty()) {
        out.add(
            ColValueFilter.builder()
                .colName("db_operation_name")
                .strValue(db.getOperation())
                .build());
      }
    }
    out.addAll(buildAttributeStringFilters(request.getStringAttributesFilter()));
    out.addAll(buildAttributeNumberFilters(request.getNumberAttributesFilter()));
    return out;
  }

  private List<String> getTimeRangeAndDurationClauses(SpansQueryStatsRequest request) {
    var out = new ArrayList<String>();
    var ts = request.getTimestampFilter();
    if (ts != null) {
      out.add("ts_start_ns >= " + ts.getTsStartNanos());
      out.add("ts_end_ns <= " + ts.getTsEndNanos());
    }
    var duration = request.getDurationFilter();
    if (duration != null) {
      var minNs = duration.getDurMinMillis() * 1_000_000L;
      var maxNs = duration.getDurMaxMillis() * 1_000_000L;
      out.add("(ts_end_ns - ts_start_ns) >= " + minNs);
      out.add("(ts_end_ns - ts_start_ns) <= " + maxNs);
    }
    return out;
  }

  private List<ColValueFilter> buildAttributeStringFilters(List<StringAttributeFilter> filters) {
    if (filters == null) return Collections.emptyList();
    return filters.stream()
        .filter(Objects::nonNull)
        .map(
            filter -> {
              var key = filter.getKey();
              var value = filter.getValue();
              var bucket = ChSpanAttributeBucketer.bucketForKey(key);
              var colName = "attribs_str_" + bucket + "['" + key + "']";
              return ColValueFilter.builder().colName(colName).strValue(value).build();
            })
        .toList();
  }

  private List<ColValueFilter> buildAttributeNumberFilters(List<NumberAttributeFilter> filters) {
    if (filters == null) return Collections.emptyList();
    return filters.stream()
        .filter(Objects::nonNull)
        .map(
            filter -> {
              var key = filter.getKey();
              var value = filter.getValue();
              var bucket = ChSpanAttributeBucketer.bucketForKey(key);
              var colName = "attribs_number_" + bucket + "['" + key + "']";
              return ColValueFilter.builder().colName(colName).doubleValue(value).build();
            })
        .toList();
  }
}
