/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.okapi.data.model.DashboardVariable;

@Entity
@Table(name = "dashboard_variables")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVariableEntity {
  @EmbeddedId private Id id;

  private String tag;

  @Enumerated(EnumType.STRING)
  @Column(name = "var_type", nullable = false)
  private DashboardVariable.Type varType;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id", nullable = false)
    private String orgId;

    @Column(name = "dashboard_id", nullable = false)
    private String dashboardId;

    @Column(name = "version_id", nullable = false)
    private String versionId;

    @Column(name = "var_name", nullable = false)
    private String varName;
  }
}
