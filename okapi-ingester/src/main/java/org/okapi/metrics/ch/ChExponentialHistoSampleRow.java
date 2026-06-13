/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ChExponentialHistoSampleRow {
  @SerializedName("metric_name")
  String metric;

  Map<String, String> tags;

  @SerializedName("histo_type")
  ChHistoSample.HISTO_TYPE histoType;

  @SerializedName("ts_start")
  long tsStart;

  @SerializedName("ts_end")
  long tsEnd;

  int scale;

  @SerializedName("zero_threshold")
  double zeroThreshold;

  @SerializedName("zero_count")
  long zeroCount;

  @SerializedName("positive_offset")
  int positiveOffset;

  @SerializedName("positive_counts")
  long[] positiveCounts;

  @SerializedName("negative_offset")
  int negativeOffset;

  @SerializedName("negative_counts")
  long[] negativeCounts;

  Double sum;
  long count;
  String unit;
}
