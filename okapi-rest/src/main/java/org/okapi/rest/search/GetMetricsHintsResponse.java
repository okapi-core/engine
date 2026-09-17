/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import java.util.List;
import lombok.*;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@ToString
@Builder
public class GetMetricsHintsResponse {
  List<String> metricHints;
  List<String> tagHints;
  TagValueCompletion tagValueHints;
}
