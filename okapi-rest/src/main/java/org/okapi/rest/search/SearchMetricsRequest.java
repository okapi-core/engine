/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.search;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@JsonClassDescription(
"""
Search criteria against which metric paths will be matched. Note apart from time window parameters, all other parameters are optional.
However, the size of the response might be quite big depending on metrics cardinality. It is recommended to set atleast one search criteria.
Note only metric paths which match all criteria will be returned. Setting conflicting values will lead to 0 search results.
""")
@ToString
public class SearchMetricsRequest {
  @ToolParam(
      description =
"""
Start time for time window. This should be linux epoch time in milliseconds.
We'll only return metric paths if a metric was emitted with timestamp >= tsStartMillis. This parameter is required.
""")
  long tsStartMillis;

  @ToolParam(
      description =
"""
End time for time window. This should be linux epoch time in milliseconds.
We'll only return metric paths if a metric was emitted with timestamp <= tsEndMillis. This parameter is required.
""")
  long tsEndMillis;

  @ToolParam(
      description =
"""
Metric pattern to match. This should be a valid RE2 regular expression.
""",
      required = false)
  String metricNamePattern;

  @ToolParam(
      description =
"""
Metric name. This will be matched exactly against the metric name. Matching is case sensitive.
""",
      required = false)
  String metricName;

  @ToolParam(
      description =
"""
List label-value filters to match. Filters will are matched as an AND of each filter.
""",
      required = false)
  List<LabelValueFilter> valueFilters;

  @ToolParam(
      description =
          "List label-value pattern filters to match. Filters will are matched as an AND of each filter.",
      required = false)
  List<LabelValuePatternFilter> patternFilters;

  @ToolParam(
      description =
"""
Optional cross-field filter. If set, the metric name or any tag value must match the
filter's value (exact) or pattern (RE2). Applied before all other filters are applied. This parameter is optional.
""",
      required = false)
  AnyMetricOrValueFilter anyMetricOrValueFilter;
}
