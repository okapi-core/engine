/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonClassDescription("Result of a span search query, containing matched spans.")
@ToString
public class SpanQueryV2SummaryResponse {
  @JsonPropertyDescription("Total count of results which match this query.")
  long count;
}
