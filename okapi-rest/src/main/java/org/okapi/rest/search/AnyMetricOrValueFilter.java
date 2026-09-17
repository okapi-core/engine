/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@ToString
public class AnyMetricOrValueFilter {

  @ToolParam(
      description =
"""
Exact value to match. Either the metric name or any tag value in the metric path must equal
this string exactly. Takes precedence over pattern if both are set.""",
      required = false)
  String value;

  @ToolParam(
      description =
"""
RE2 regular expression. Either the metric name or any tag value in the metric path must fully match this pattern.
Ignored when value is set.""",
      required = false)
  String pattern;
}
