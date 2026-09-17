/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Getter
public class MetricsQueryRequest {

  String grammar;
  String query;
  long startMs;
  long endMs;
  String metricType;
  String resolution;
}
