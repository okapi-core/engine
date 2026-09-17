/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ChLogFilterClause {
  String sql;
}
