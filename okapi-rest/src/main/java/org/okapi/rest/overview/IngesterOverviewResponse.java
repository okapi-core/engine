/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.overview;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class IngesterOverviewResponse {
  String window;
  long startMillis;
  long endMillis;
  long metricsEvents;
  long traceEvents;
  long logEvents;
}
