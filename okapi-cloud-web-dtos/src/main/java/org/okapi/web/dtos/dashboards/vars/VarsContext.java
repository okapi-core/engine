/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.vars;

import java.util.Map;
import lombok.Value;

@Value
public class VarsContext {
  Map<String, String> varValues;
}
