/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class FlameGraphNode {
  @NotNull String spanId;
  @NotNull String parentSpanId;
  @NotNull String serviceName;
  String kind;
  @NotNull long startNs;
  @NotNull long endNs;
  @NotNull long durationNs;
  @NotNull long offsetNs;
  List<FlameGraphNode> children;
}
