/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import java.util.List;
import java.util.Map;
import lombok.*;
import org.okapi.rest.common.UnionValue;

@AllArgsConstructor
@Builder
@Getter
@NoArgsConstructor
@ToString
@JsonClassDescription("Tabular result of an Okapi TraceQL query.")
public class OkapiTraceQlResponse {
  OkapiTraceQlResultKind kind;
  List<Map<String, UnionValue>> rows;
}
