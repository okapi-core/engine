/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.vars;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.okapi.web.dtos.constraints.TimeConstraint;

@Value
public class GetVarHintsRequest {
  DASH_VAR_TYPE varType;
  String tag;
  @NotNull TimeConstraint constraint;
}
