/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.eval.nodes;

public record SmoothedExpr(LogicalExpr inner) implements LogicalExpr {}
