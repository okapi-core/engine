/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.vars;

import lombok.Value;

@Value
public class GetVarResponse {
  DASH_VAR_TYPE type;
  String name;
  String tag;
}
