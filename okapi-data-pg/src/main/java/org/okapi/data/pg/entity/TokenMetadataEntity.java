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
import org.okapi.data.model.TokenStatus;

@Entity
@Table(name = "token_metadata")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TokenMetadataEntity {
  @EmbeddedId private Id id;

  @Column(name = "creator_id")
  private String creatorId;

  @Column(name = "created_at")
  private Long createdAt;

  @Enumerated(EnumType.STRING)
  private TokenStatus status;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "org_id")
    private String orgId;

    @Column(name = "token_id")
    private String tokenId;
  }
}
