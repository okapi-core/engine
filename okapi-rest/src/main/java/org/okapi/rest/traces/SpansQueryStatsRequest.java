/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SpansQueryStatsRequest {
  String traceId;
  String kind;
  DbFilters dbFilters;
  DurationFilter durationFilter;
  HttpFilters httpFilters;
  ServiceFilter serviceFilter;
  TimestampFilter timestampFilter;
  List<StringAttributeFilter> stringAttributesFilter;
  List<NumberAttributeFilter> numberAttributesFilter;
  NumericalAggConfig numericalAgg;
  DistributionSummaryConfig summaryConfig;
  List<String> attributes;
}
