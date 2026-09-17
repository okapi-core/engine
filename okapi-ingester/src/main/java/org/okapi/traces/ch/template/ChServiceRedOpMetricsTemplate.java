/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch.template;

import lombok.Builder;
import lombok.Value;
import org.okapi.rest.traces.TimestampFilter;

@Value
@Builder
public class ChServiceRedOpMetricsTemplate {
  String table;
  String serviceName;
  String spanName;
  String bucketStartExpr;
  String durationExpr;
  TimestampFilter timestampFilter;
}
