/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import com.clickhouse.client.api.query.GenericRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class OverviewRow {
  long metricsEvents;
  long traceEvents;
  long logEvents;

  public static OverviewRow from(GenericRecord record) {
    return OverviewRow.builder()
        .metricsEvents(record.getLong("metrics_events"))
        .traceEvents(record.getLong("trace_events"))
        .logEvents(record.getLong("log_events"))
        .build();
  }
}
