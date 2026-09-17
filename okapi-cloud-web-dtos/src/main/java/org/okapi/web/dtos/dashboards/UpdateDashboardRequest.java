/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class UpdateDashboardRequest {
  String desc;
  String title;
  List<String> tags;
  List<String> rowIds;
  Boolean isFavorite;
}
