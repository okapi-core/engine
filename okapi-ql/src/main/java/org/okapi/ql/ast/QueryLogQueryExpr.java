/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import java.util.List;
import lombok.Value;

@Value
public final class QueryLogQueryExpr implements LogQueryExpr {
  LogQueryExpr with;
  LogQueryExpr filter;
  List<PipelineOp> pipeline;

  public QueryLogQueryExpr(LogQueryExpr with, LogQueryExpr filter, List<PipelineOp> pipeline) {
    this.with = with;
    this.filter = filter;
    this.pipeline = pipeline;
  }
}
