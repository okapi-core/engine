/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class UserEntityRelation {
  private String userId;
  private EntityRelationId edgeId;
  private EdgeAttributes edgeAttributes;
}
