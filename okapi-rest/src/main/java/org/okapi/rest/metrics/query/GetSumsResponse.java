/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.query;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@JsonClassDescription("Sum/counter query results.")
@ToString
public class GetSumsResponse {
  @JsonPropertyDescription("List of sum intervals covering the queried time range.")
  List<Sum> sums;
}
