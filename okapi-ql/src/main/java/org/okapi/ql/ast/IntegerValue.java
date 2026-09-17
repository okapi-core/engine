/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import lombok.Value;

@Value
public final class IntegerValue implements LiteralValue {
  long value;
}
