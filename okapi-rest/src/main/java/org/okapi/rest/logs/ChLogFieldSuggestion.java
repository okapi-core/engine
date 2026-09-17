/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.okapi.rest.common.UNION_TYPE;
import org.okapi.rest.common.UnionValue;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogFieldSuggestion {
  String name;
  UNION_TYPE type;
  long count;
  List<UnionValue> exampleValues;
}
