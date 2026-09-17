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
public class ServiceFilter {
  @ToolParam(
      description =
"""
Name of the service that emitted the span. Use this to find spans emitted from inside the service `service`.
""",
      required = false)
  String service;

  @ToolParam(
      description =
"""
Name of the downstream. Setting this filter means trying to find spans which for requests which are made TO this `peer`.
Setting both `peer` and `service` will return spans related to code paths where there's communication between `service` and `peer`.
""",
      required = false)
  String peer;
}
