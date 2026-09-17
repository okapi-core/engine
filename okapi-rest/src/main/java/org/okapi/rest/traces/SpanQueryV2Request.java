/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import lombok.*;
import org.springframework.ai.tool.annotation.ToolParam;

@Builder
@AllArgsConstructor
@JsonClassDescription(
"""
Encapsulates search filters used to discover spans. Filters are composed via an AND operation i.e. only spans which match all filters are returned."
Not every filter needs to be set. When in doubt, set the minimum sure set of filters.
""")
@Getter
@NoArgsConstructor
@ToString
public class SpanQueryV2Request {
  @ToolParam(
      description =
"""
Filter to spans belonging to this exact trace ID. This parameter is optional.
""",
      required = false)
  String traceId;

  @ToolParam(
      description =
"""
Filter to the span with this exact span ID. SpanIds are hex-encoded byte strings. This parameter is optional.
""",
      required = false)
  String spanId;

  @ToolParam(
      description =
"""
Span kind to filter. Usually the values are SPAN_KIND_SERVER or SPAN_KIND_CLIENT.
If spans are not found, its likely this field wasn't set in the spans submitted by an application.
Its a good idea to not set this filter in case no spans are found. This parameter is optional.
""",
      required = false)
  String kind;

  @ToolParam(
      description =
"""
Filters scoped to database spans: system, collection, namespace, and operation. This parameter is optional.
""",
      required = false)
  DbFilters dbFilters;

  @ToolParam(
      description =
"""
Restricts results to spans whose duration falls within the given millisecond range.  This parameter is optional.
""",
      required = false)
  DurationFilter durationFilter;

  @ToolParam(
      description =
"""
Filters scoped to HTTP spans: method, status code, origin, and host.
""",
      required = false)
  HttpFilters httpFilters;

  @ToolParam(
      description =
"""
Filters by originating service name and/or downstream peer service.
""",
      required = false)
  ServiceFilter serviceFilter;

  @ToolParam(
      description =
"""
  Time window for the query, expressed in nanoseconds since Unix epoch.
""")
  TimestampFilter timestampFilter;

  @ToolParam(
      description =
"""
List of span attribute filters where each entry matches a string attribute key to an exact string value.
""",
      required = false)
  List<StringAttributeFilter> stringAttributesFilter;

  @ToolParam(
      description =
"""
List of span attribute filters where each entry matches a string attribute key to an exact numeric value.
""",
      required = false)
  List<NumberAttributeFilter> numberAttributesFilter;
}
