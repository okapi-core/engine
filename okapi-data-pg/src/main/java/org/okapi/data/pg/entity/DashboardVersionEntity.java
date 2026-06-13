/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dashboard_versions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVersionEntity {
  @EmbeddedId private Id id;

  private String status;

  @Column(name = "created_at")
  private Long createdAt;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "spec_hash")
  private String specHash;

  private String note;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "dashboard_id")
    private String dashboardId;

    @Column(name = "version_id")
    private String versionId;
  }
}
