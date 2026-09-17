/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.responses;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonClassDescription(
"""
SpanQueryV2Request match counts. overallCount is for the full request, and per-filter counts are computed
with the specified filter removed. A null count means the filter was not set in the request.
""")
public class FilterContribution {
  @JsonPropertyDescription(
"""
Count of spans matching the full SpanQueryV2Request (all filters applied).
""")
  Long overallCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.traceId removed. Null means traceId was not set.
""")
  Long traceIdRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.spanId removed. Null means spanId was not set.
""")
  Long spanIdRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.kind removed. Null means kind was not set.
""")
  Long kindRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.dbFilters removed. Null means dbFilters was not set.
""")
  Long dbFiltersRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.durationFilter removed. Null means durationFilter was not set.
""")
  Long durationFilterRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.httpFilters removed. Null means httpFilters was not set.
""")
  Long httpFiltersRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.serviceFilter removed. Null means serviceFilter was not set.
""")
  Long serviceFilterRemovedCount;

  @JsonPropertyDescription(
"""
Count of spans matching the full request with SpanQueryV2Request.timestampFilter removed. Null means timestampFilter was not set.
""")
  Long timestampFilterRemovedCount;

  @JsonPropertyDescription(
"""
Per-key counts for SpanQueryV2Request.stringAttributesFilter with each attribute filter removed in turn.
Null means stringAttributesFilter was not set.
""")
  Map<String, Long> stringAttributesRemovedCounts;

  @JsonPropertyDescription(
"""
Per-key counts for SpanQueryV2Request.numberAttributesFilter with each attribute filter removed in turn.
Null means numberAttributesFilter was not set.
""")
  Map<String, Long> numberAttributesRemovedCounts;
}
