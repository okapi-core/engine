/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpansFlameGraphResponse {
  String traceId;
  long traceStartNs;
  long traceEndNs;
  List<FlameGraphNode> roots;
}
