/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

public sealed interface LogQueryExpr
    permits QueryLogQueryExpr,
        AndLogQueryExpr,
        OrLogQueryExpr,
        NotLogQueryExpr,
        ComparisonLogQueryExpr,
        FreeTextLogQueryExpr {}
