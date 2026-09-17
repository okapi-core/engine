/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

public final class ChSpanStatsQueryBuilder {
  private ChSpanStatsQueryBuilder() {}

  public static String buildBucketStartExpr(RES_TYPE resType) {
    if (resType == null) {
      return "toUnixTimestamp(toStartOfSecond(toDateTime64(ts_start_ns/1000000000, 0))) * 1000";
    }
    return switch (resType) {
      case SECONDLY ->
          "toUnixTimestamp(toStartOfSecond(toDateTime64(ts_start_ns/1000000000, 0))) * 1000";
      case MINUTELY ->
          "toUnixTimestamp(toStartOfMinute(toDateTime(ts_start_ns/1000000000))) * 1000";
      case HOURLY -> "toUnixTimestamp(toStartOfHour(toDateTime(ts_start_ns/1000000000))) * 1000";
    };
  }

  public static String buildAggType(String valueExpr, AGG_TYPE aggType) {
    return switch (aggType) {
      case AVG -> "avg(" + valueExpr + ")";
      case MIN -> "min(" + valueExpr + ")";
      case MAX -> "max(" + valueExpr + ")";
      case SUM -> "sum(" + valueExpr + ")";
      case P50 -> "quantile(0.50)(" + valueExpr + ")";
      case P75 -> "quantile(0.75)(" + valueExpr + ")";
      case P90 -> "quantile(0.90)(" + valueExpr + ")";
      case P95 -> "quantile(0.95)(" + valueExpr + ")";
      case P99 -> "quantile(0.99)(" + valueExpr + ")";
      case COUNT -> "count()";
    };
  }

  public static String buildAggClause(AGG_TYPE aggType, String attr) {
    var valueExpr = ChSpanAtttributes.getValueExpr(attr, true);
    return buildAggType(valueExpr, aggType);
  }
}
