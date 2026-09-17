/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpanFilterRest {
  @NotNull String kind; // LEVEL, TRACE, REGEX, AND, OR
  byte[] traceId;
  byte[] spanId;
  SpanFilterRest left;
  SpanFilterRest right;
}
