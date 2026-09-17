/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces.red;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.okapi.metrics.pojos.RES_TYPE;
import org.okapi.rest.traces.TimestampFilter;
import org.springframework.ai.tool.annotation.ToolParam;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class ServiceRedRequest {
  // RED for that specific service
  // RED for each operation
  // edge-RED for each of this service.
  @ToolParam(
      description =
"""
Time window for the RED metrics query, in nanoseconds since Unix epoch.
Use `timeRangeNanos` tool with the appropriate duration to get a range that starts at the current time.
""")
  TimestampFilter timestampFilter;

  @ToolParam(
      description =
"""
Name of the service for which to compute RED metrics (rate, error rate, duration).
""")
  @NotNull(message = "service must be specified")
  String service;

  @ToolParam(
      description =
"""
Time bucket resolution at which the RED metrics are calculated: SECONDLY, MINUTELY, or HOURLY.
RED metrics are returned as a Gauge hence the resolution should be specified.
""")
  @NotNull(message = "resolution must be provided")
  RES_TYPE resType;
}
