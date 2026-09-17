/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ql.ast;

import lombok.NonNull;
import lombok.Value;

@Value
public final class IdentifierValue implements LiteralValue {
  @NonNull String value;
}
