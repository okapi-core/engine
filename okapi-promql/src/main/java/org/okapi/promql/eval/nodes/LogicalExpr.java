/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public sealed interface LogicalExpr
    permits AggregateExpr,
        AtExpr,
        BinaryOpExpr,
        FunctionExpr,
        InstantizeExpr,
        LiteralExpr,
        OffsetExpr,
        RangeSelectorExpr,
        SelectorExpr,
        SmoothedExpr,
        StringLiteralExpr,
        SubqueryExpr {}
