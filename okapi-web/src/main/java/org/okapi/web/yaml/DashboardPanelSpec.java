/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.yaml;

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
public class DashboardPanelSpec {
  String id;
  String title;
  String note;
  GRAMMAR grammar;
  List<PanelQuerySpec> queries;
}
