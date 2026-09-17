/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.okapi.metrics.pojos.AGG_TYPE;
import org.okapi.metrics.pojos.RES_TYPE;

public class ChSpanStatsQueryBuilderTests {

  @Test
  void buildBucketStartExprTest() {
    assertTrue(
        ChSpanStatsQueryBuilder.buildBucketStartExpr(RES_TYPE.SECONDLY)
            .contains("toStartOfSecond"));
    assertTrue(
        ChSpanStatsQueryBuilder.buildBucketStartExpr(RES_TYPE.MINUTELY)
            .contains("toStartOfMinute"));
    assertTrue(
        ChSpanStatsQueryBuilder.buildBucketStartExpr(RES_TYPE.HOURLY).contains("toStartOfHour"));
  }

  @Test
  void buildAggClauseTest() {
    String avgClause = ChSpanStatsQueryBuilder.buildAggClause(AGG_TYPE.AVG, "custom.number");
    assertEquals("avg(attribs_number_4['custom.number'])", avgClause);
    String p95Clause = ChSpanStatsQueryBuilder.buildAggClause(AGG_TYPE.P95, "latency");
    assertEquals("quantile(0.95)(attribs_number_8['latency'])", p95Clause);
  }

  @Test
  void buildAggClauseCountTest() {
    String countClause = ChSpanStatsQueryBuilder.buildAggClause(AGG_TYPE.COUNT, "ignored");
    assertEquals("count()", countClause);
  }
}
