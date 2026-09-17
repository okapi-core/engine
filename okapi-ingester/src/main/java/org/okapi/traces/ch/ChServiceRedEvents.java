/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ChServiceRedEvents {

  @SerializedName("ts_start_nanos")
  long tsStartNanos;

  @SerializedName("ts_end_nanos")
  long tsEndNanos;

  @SerializedName("service_name")
  String serviceName;

  @SerializedName("span_name")
  String spanName;

  @SerializedName("peer_service_name")
  String peerServiceName;

  @SerializedName("span_status")
  SpanStatus spanStatus;

  @SerializedName("span_kind")
  SpanKind spanKind;

  @SerializedName("duration_nanos")
  long durationNanos;
}
