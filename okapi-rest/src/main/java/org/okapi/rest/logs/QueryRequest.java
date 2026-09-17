/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.logs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class QueryRequest {
  public long start;
  public long end;
  public int limit;
  public FilterNode filter;
}
