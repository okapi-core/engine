/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.okapi.rest.common.UnionValue;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogFieldValueSuggestion {
  UnionValue value;
  long count;
}
