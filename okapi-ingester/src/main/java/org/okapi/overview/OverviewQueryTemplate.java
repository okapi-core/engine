/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.overview;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OverviewQueryTemplate {
  String gaugeTable;
  String sumTable;
  String histoTable;
  String exponentialHistoTable;
  String tracesTable;
  String logsTable;
  long startMillis;
  long endMillis;
  long startNanos;
  long endNanos;
}
