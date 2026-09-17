/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.traces;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class SpanQueryRequest {

  long start;
  long end;
  Integer limit = 1000;
  String pageToken;
  SpanFilterRest filter;

  @Builder
  public record Attribute(String name, String value, String pattern) {}
}
