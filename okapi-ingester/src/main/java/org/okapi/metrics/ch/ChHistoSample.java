/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
@Builder
@Getter
public class ChHistoSample {
  public enum HISTO_TYPE {
    DELTA,
    CUMULATIVE
  }

  @SerializedName("metric_name")
  String metric;

  Map<String, String> tags;

  @SerializedName("histo_type")
  HISTO_TYPE histoType;

  float sum;
  Long count;
  String unit;

  @SerializedName("ts_start")
  long tsStart;

  @SerializedName("ts_end")
  long tsEnd;

  float min;
  float max;
  float[] buckets;
  long[] counts;
}
