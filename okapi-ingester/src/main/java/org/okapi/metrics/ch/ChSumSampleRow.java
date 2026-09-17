/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import com.google.gson.annotations.SerializedName;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.ch.AbstractChRow;

@AllArgsConstructor
@Getter
@Builder
public class ChSumSampleRow extends AbstractChRow {
  @SerializedName("metric_name")
  String metricName;

  Map<String, String> tags;

  @SerializedName("ts_start")
  long tsStart;

  @SerializedName("ts_end")
  long tsEnd;

  String unit;

  double value;

  @SerializedName("sums_type")
  CH_SUM_TYPE sumType;
}
