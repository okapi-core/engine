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
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federated_sources")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FederatedSourceEntity {
  @EmbeddedId private Id id;

  @Column(name = "source_type")
  private String sourceType;

  @Column(name = "registration_token")
  private String registrationToken;

  @Column(name = "created_at")
  private Instant created;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "source_name")
    private String sourceName;
  }
}
