/*
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dashboard_panels")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardPanelEntity {
  @EmbeddedId private Id id;
  private String note;
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "query_config")
  private String queryConfig;

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

    @Column(name = "row_id")
    private String rowId;

    @Column(name = "panel_id")
    private String panelId;
  }
}
