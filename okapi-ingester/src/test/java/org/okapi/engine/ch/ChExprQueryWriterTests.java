/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.ch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.okapi.engine.ch.ChQueryModel.BinaryOperator;
import org.okapi.engine.ch.ChQueryModel.BinaryPredicate;
import org.okapi.engine.ch.ChQueryModel.ColumnExpr;
import org.okapi.engine.ch.ChQueryModel.LiteralExpr;
import org.okapi.engine.ch.ChQueryModel.LogQuery;
import org.okapi.engine.ch.ChQueryModel.OrderByItem;
import org.okapi.engine.ch.ChQueryModel.RawExpr;
import org.okapi.engine.ch.ChQueryModel.ScalarType;
import org.okapi.engine.ch.ChQueryModel.SelectItem;
import org.okapi.engine.ch.ChQueryModel.SortDirection;
import org.okapi.rest.logs.OkapiLogQlResultKind;

class ChExprQueryWriterTests {
  @Test
  void rendersSelectWhereGroupOrderAndLimit() {
    var query =
        LogQuery.builder()
            .table("logs")
            .selectItems(
                List.of(
                    new SelectItem(new ColumnExpr("service_name", ScalarType.STRING), "service"),
                    new SelectItem(new RawExpr("count()", ScalarType.NUMBER), "count")))
            .where(
                new BinaryPredicate(
                    new ColumnExpr("log_level", ScalarType.NUMBER),
                    BinaryOperator.GTE,
                    new LiteralExpr(13, ScalarType.NUMBER)))
            .groupBy(List.of(new ColumnExpr("service_name", ScalarType.STRING)))
            .orderBy(
                List.of(
                    new OrderByItem(new RawExpr("count", ScalarType.NUMBER), SortDirection.DESC)))
            .limit(10)
            .resultKind(OkapiLogQlResultKind.COUNT_BY)
            .build();

    var writer = new ChExprQueryWriter();
    writer.visit(query);

    assertEquals(
        "SELECT service_name AS service, count() AS count FROM logs WHERE log_level >= 13 GROUP BY service_name ORDER BY count DESC LIMIT 10",
        writer.getQuery());
  }
}
