/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ChLogsQueryTemplate {
  String table;
  Long tsStartNs;
  Long tsEndNs;
  List<ChLogFilterClause> filters;
  int limit;
}
