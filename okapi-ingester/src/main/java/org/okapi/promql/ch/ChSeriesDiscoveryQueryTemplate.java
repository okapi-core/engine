/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.ch;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChSeriesDiscoveryQueryTemplate {
  String table;
  String metric;
  long startMs;
  long endMs;
}
