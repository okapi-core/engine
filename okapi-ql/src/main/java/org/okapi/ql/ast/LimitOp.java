/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import lombok.Value;

@Value
public final class LimitOp implements PipelineOp {
  int count;
}
