/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.okapi.rest.traces.DbFilters;
import org.okapi.rest.traces.DurationFilter;
import org.okapi.rest.traces.HttpFilters;
import org.okapi.rest.traces.ServiceFilter;
import org.okapi.rest.traces.TimestampFilter;

@Value
@Builder
public class ChSpansStatsCountTemplate {
  String table;
  String traceId;
  String kind;
  ServiceFilter serviceFilter;
  HttpFilters httpFilters;
  DbFilters dbFilters;
  TimestampFilter timestampFilter;
  DurationFilter durationFilter;
  List<ChSpanStringAttributeFilter> stringFilters;
  List<ChSpanNumberAttributeFilter> numberFilters;
}
