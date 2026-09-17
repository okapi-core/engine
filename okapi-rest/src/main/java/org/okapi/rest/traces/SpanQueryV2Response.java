/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonClassDescription("Result of a span search query, containing matched spans.")
@ToString
public class SpanQueryV2Response {
  @JsonPropertyDescription("List of spans matching all supplied query filters.")
  List<SpanRowV2> items;
}
