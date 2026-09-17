/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboards")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardEntity {
  @EmbeddedId private Id id;
  private String creator;

  @Column(name = "last_editor")
  private String lastEditor;

  @Column(name = "created_at")
  private Instant created;

  @Column(name = "updated_at")
  private Instant updatedTime;

  private String title;

  @Column(name = "description")
  private String desc;

  @JdbcTypeCode(SqlTypes.JSON)
  private String tags;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "row_order")
  private String rowOrder;

  @Column(name = "active_version")
  private String activeVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dashboard_vars")
  private String dashVars;

  private Long version;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "dashboard_id")
    private String dashboardId;
  }
}
