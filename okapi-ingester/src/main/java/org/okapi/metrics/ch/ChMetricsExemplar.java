/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ChMetricsExemplar {
  @SerializedName("ts_nanos")
  Long tsNanos;

  @SerializedName("metric_name")
  String metricName;

  Map<String, String> tags;

  @SerializedName("span_id")
  String spanId;

  @SerializedName("trace_id")
  String traceId;

  @SerializedName("kind")
  String kind;

  @SerializedName("attributes_kv_list_json")
  String attributesKvListJson;

  @SerializedName("number_value")
  Double numberValue;

  @SerializedName("string_value")
  String stringValue;
}
