/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.exemplar;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.rest.metrics.Exemplar;
import org.okapi.rest.traces.TimestampFilter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetExemplarsResponse {
  String metric;
  Map<String, String> labels;
  TimestampFilter timeFilter;
  List<Exemplar> exemplars;
}
