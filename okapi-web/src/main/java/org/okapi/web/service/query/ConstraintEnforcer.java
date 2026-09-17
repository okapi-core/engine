/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import org.okapi.rest.metrics.query.GetMetricsRequest;
import org.okapi.web.dtos.constraints.TimeConstraint;

public class ConstraintEnforcer {
  public static GetMetricsRequest applyTimeConstraint(
      GetMetricsRequest request, TimeConstraint constraint) {
    var constrained = request.toBuilder();
    constrained.start(constraint.getStart());
    constrained.end(constraint.getEnd());
    return constrained.build();
  }
}
