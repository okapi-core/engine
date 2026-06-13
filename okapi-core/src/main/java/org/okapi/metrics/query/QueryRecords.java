/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.query;

import java.util.List;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

public class QueryRecords {
  public record Slice(
      String series, long from, long to, RES_TYPE resolution, AGG_TYPE aggregation) {}

  public record QueryResult(String name, List<Long> timestamps, List<Float> values) {}
}
