/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

public sealed interface PipelineOp permits SelectOp, RemoveOp, SortOp, LimitOp, CountByOp {
  enum Direction {
    UNSPECIFIED,
    ASC,
    DESC
  }
}
