/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.okapi.ch.GenericRecordReader.applyMappers;
import static org.okapi.ch.GenericRecordReader.getMap;

import com.clickhouse.client.api.query.GenericRecord;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.rest.common.KeyValueJson;
import org.okapi.rest.common.NumberValue;
import org.okapi.rest.metrics.Exemplar;

@Builder
@AllArgsConstructor
@Getter
public class ChExemplarRow {
  @SerializedName("ts_nanos")
  long tsNanos;

  @SerializedName("metric_name")
  String metricName;

  Map<String, String> tags;

  @SerializedName("span_id")
  String spanId;

  @SerializedName("trace_id")
  String traceId;

  String kind;

  @SerializedName("double_value")
  Double doubleValue;

  @SerializedName("int_value")
  Long intValue;

  @SerializedName("attributes_kv_list_json")
  String attributesKvListJson;

  public static ChExemplarRow fromRecord(GenericRecord genericRecord) {
    var builder = ChExemplarRow.builder();
    Map<String, Function<Long, ChExemplarRow.ChExemplarRowBuilder>> longMappers =
        java.util.Map.of("ts_nanos", builder::tsNanos, "int_value", builder::intValue);
    Map<String, Function<String, ChExemplarRow.ChExemplarRowBuilder>> stringMappers =
        Map.of(
            "metric_name",
            builder::metricName,
            "trace_id",
            builder::traceId,
            "span_id",
            builder::spanId,
            "attributes_kv_list_json",
            builder::attributesKvListJson);
    Map<String, Function<Map<String, String>, ChExemplarRow.ChExemplarRowBuilder>> tagMappers =
        java.util.Map.of("tags", builder::tags);
    Map<String, Function<Double, ChExemplarRow.ChExemplarRowBuilder>> doubleMappers =
        Map.of("double_value", builder::doubleValue);
    var attribs = genericRecord.getValues();
    applyMappers(genericRecord, genericRecord::getLong, longMappers);
    applyMappers(genericRecord, genericRecord::getString, stringMappers);
    applyMappers(genericRecord, genericRecord::getDouble, doubleMappers);
    applyMappers(genericRecord, a -> getMap("tags", attribs), tagMappers);
    return builder.build();
  }

  public Exemplar toRestExemplar(Gson gson) {
    var typeToken =
        new TypeToken<List<KeyValueJson>>() {
          {
          }
        };
    List<KeyValueJson> deserialized = gson.fromJson(this.attributesKvListJson, typeToken.getType());
    return Exemplar.builder()
        .metric(this.metricName)
        .tags(this.tags)
        .tsNanos(this.tsNanos)
        .kv(deserialized)
        .measurement(
            NumberValue.builder().aDouble(this.doubleValue).anInteger(this.intValue).build())
        .spanId(this.spanId)
        .traceId(this.traceId)
        .build();
  }
}
