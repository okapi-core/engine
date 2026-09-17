/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class NumberAttributeFilter {
  @JsonPropertyDescription("Span attribute key to match.")
  String key;

  @JsonPropertyDescription("Exact numeric value the attribute must equal.")
  Double value;
}
