/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.query.GenericRecord;
import org.junit.jupiter.api.Test;

class OverviewRowTest {

  @Test
  void unmarshalsClickHouseAliasesIntoPojo() {
    GenericRecord record = mock(GenericRecord.class);
    when(record.getLong("metrics_events")).thenReturn(11L);
    when(record.getLong("trace_events")).thenReturn(22L);
    when(record.getLong("log_events")).thenReturn(33L);

    OverviewRow row = OverviewRow.from(record);

    assertEquals(11L, row.getMetricsEvents());
    assertEquals(22L, row.getTraceEvents());
    assertEquals(33L, row.getLogEvents());
  }

  @Test
  void readsExpectedClickHouseColumnNames() {
    GenericRecord record = mock(GenericRecord.class);
    when(record.getLong("metrics_events")).thenReturn(1L);
    when(record.getLong("trace_events")).thenReturn(2L);
    when(record.getLong("log_events")).thenReturn(3L);

    OverviewRow.from(record);

    verify(record).getLong("metrics_events");
    verify(record).getLong("trace_events");
    verify(record).getLong("log_events");
  }
}
