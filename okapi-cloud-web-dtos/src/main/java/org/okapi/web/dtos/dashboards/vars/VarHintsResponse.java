/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards.vars;

import java.util.List;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;

@Value
@ToString
@Builder
public class VarHintsResponse {
  List<String> suggestions;
}
