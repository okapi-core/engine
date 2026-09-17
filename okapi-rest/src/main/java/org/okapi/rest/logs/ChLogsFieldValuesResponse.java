/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
public class ChLogsFieldValuesResponse {
  List<ChLogFieldValueSuggestion> values;
}
