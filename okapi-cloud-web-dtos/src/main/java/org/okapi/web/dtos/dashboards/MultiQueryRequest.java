/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.okapi.grammar.GRAMMAR;
import org.okapi.web.dtos.constraints.TimeConstraint;
import org.okapi.web.dtos.dashboards.vars.VarsContext;

@AllArgsConstructor
@Getter
@Builder
public class MultiQueryRequest {
  TimeConstraint timeConstraint;
  VarsContext varsContext;
  GRAMMAR grammar;
  List<QueryConfig> queries;
}
