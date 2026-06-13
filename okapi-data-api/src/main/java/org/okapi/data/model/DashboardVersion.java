/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class DashboardVersion {
  private String orgId;
  private String dashboardId;
  private String versionId;
  private String status;
  private Long createdAt;
  private String createdBy;
  private String specHash;
  private String note;
}
