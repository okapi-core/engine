/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.metrics.query.METRIC_TYPE;

@AllArgsConstructor
@Getter
@NoArgsConstructor
public class MetricEventFilter {
  @NotNull(message = "metricType is required")
  METRIC_TYPE metricType;
}
