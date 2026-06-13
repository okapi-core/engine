/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dashboardvars;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
public class DashVars {
  List<SvcOrMetricVar> svcVars;
  List<SvcOrMetricVar> metricVars;
  List<TagVar> tagVars;

  @Builder(toBuilder = true)
  public DashVars(
      List<SvcOrMetricVar> svcVars, List<SvcOrMetricVar> metricVars, List<TagVar> tagVars) {
    this.svcVars = svcVars;
    this.metricVars = metricVars;
    this.tagVars = tagVars;
  }
}
