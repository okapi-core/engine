/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
public class RelationGraphNode {
  private String entityId;
  private String relatedEntity;
  private EntityType relatedEntityType;
  private List<RelationType> relationships;
}
