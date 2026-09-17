/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class StartScaleUpResponse {
  String opId;
  String state;
  List<String> nodeIds;
}
