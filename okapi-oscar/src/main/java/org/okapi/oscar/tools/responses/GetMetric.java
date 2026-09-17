/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.responses;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.okapi.rest.metrics.query.GetGaugeResponse;
import org.okapi.rest.metrics.query.GetHistogramResponse;
import org.okapi.rest.metrics.query.GetSumsResponse;

@AllArgsConstructor
@Getter
@Setter
public class GetMetric {
  String metric;
  Map<String, String> tags;
  GetGaugeResponse gaugeResponse;
  GetHistogramResponse histogramResponse;
  GetSumsResponse sumsResponse;
}
