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
@ToString
public class StringAttributeFilter {
  @JsonPropertyDescription("Span attribute key to match.")
  String key;

  @JsonPropertyDescription("Exact string value the attribute must equal.")
  String value;
}
