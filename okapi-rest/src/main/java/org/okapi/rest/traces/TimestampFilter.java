/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class TimestampFilter {
  @ToolParam(description = "Query window start time, in nanoseconds since Unix epoch (inclusive).")
  long tsStartNanos;

  @ToolParam(description = "Query window end time, in nanoseconds since Unix epoch (exclusive).")
  long tsEndNanos;
}
