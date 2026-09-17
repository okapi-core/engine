/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.metrics.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
@Getter
public class StartScaleUpRequest {
  List<String> nodes;
}
