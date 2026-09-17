/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FilterNode {
  public String kind; // LEVEL, TRACE, REGEX, AND, OR
  public String regex;
  public String traceId;
  public Integer levelCode;
  public FilterNode left;
  public FilterNode right;
}
