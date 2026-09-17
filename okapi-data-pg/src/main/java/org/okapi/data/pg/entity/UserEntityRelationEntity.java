/*
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
import org.okapi.data.model.EntityType;
import org.okapi.data.model.UserRelationType;

@Entity
@Table(name = "user_entity_relations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntityRelationEntity {
  @EmbeddedId private Id id;

  @Column(name = "edge_timestamp")
  private Long edgeTimestamp;

  @Column(name = "edge_string_value")
  private String edgeStringValue;

  @Column(name = "edge_boolean_value")
  private Boolean edgeBooleanValue;

  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Id implements Serializable {
    @Column(name = "user_id")
    private String userId;

    @Column(name = "entity_type")
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "relation_type")
    @Enumerated(EnumType.STRING)
    private UserRelationType relationType;
  }
}
