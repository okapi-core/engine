/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationEntity {
  @Id
  @Column(name = "org_id")
  private String orgId;

  @Column(name = "org_name")
  private String orgName;

  @Column(name = "org_creator")
  private String orgCreator;

  @Column(name = "created_at")
  private Instant created;
}
