/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.okapi.data.dashboardvars.DashVars;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Getter
@Setter
public class Dashboard {
  private String orgId;
  private String dashboardId;
  private String creator;
  private String lastEditor;
  private Instant created;
  private Instant updatedTime;
  private String title;
  private String desc;
  private Tags tags;
  private ResourceOrder rowOrder;
  private String activeVersion;
  private DashVars dashVars;
  private Long version;
}
