/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.dashboards;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.grammar.GRAMMAR;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
public class GetDashboardPanelResponse {
  @NotNull String panelId;
  String title;
  String description;
  GRAMMAR grammar;
  List<QueryConfig> queries;
}
