/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.vars;

import lombok.Value;

@Value
public class CreateDashboardVarRequest {
  DASH_VAR_TYPE dashVarType;
  String name;
  String tag;
}
