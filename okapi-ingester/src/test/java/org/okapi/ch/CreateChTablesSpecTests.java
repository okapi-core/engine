/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreateChTablesSpecTests {
  @Test
  void rawPromQlNumericSamplesUseFloat64Storage() {
    assertTrue(CreateChTablesSpec.getCreateGaugeTableSpec().contains("value Float64"));
    assertTrue(CreateChTablesSpec.getCreateSumTableSpec().contains("value Float64"));
  }

  @Test
  void exponentialHistogramsUseDedicatedNativeStorage() {
    var schema = CreateChTablesSpec.getCreateExponentialHistoTableSpec();

    assertTrue(schema.contains("exponential_histo_raw_samples"));
    assertTrue(schema.contains("positive_counts Array(UInt64)"));
    assertTrue(schema.contains("negative_counts Array(UInt64)"));
    assertTrue(schema.contains("zero_threshold Float64"));
  }

  @Test
  void logsTableUsesPartitionedStructuredAttributeStorage() {
    var schema = CreateChTablesSpec.getLogsTableSpec();

    assertTrue(schema.contains("severity_text LowCardinality(String)"));
    assertTrue(schema.contains("trace_id String CODEC(ZSTD)"));
    assertTrue(schema.contains("span_id String CODEC(ZSTD)"));
    assertTrue(schema.contains("resource_attribs_str_0 Map(String, String)"));
    assertTrue(schema.contains("resource_attribs_number_9 Map(String, Float64)"));
    assertTrue(schema.contains("attribs_str_0 Map(String, String)"));
    assertTrue(schema.contains("attribs_number_9 Map(String, Float64)"));
  }
}
