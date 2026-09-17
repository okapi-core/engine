/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.model.EntityRelationId;
import org.okapi.data.model.UserEntityRelation;

public interface UserEntityRelationsDao {
  Optional<UserEntityRelation> getRelation(String userId, EntityRelationId edgeId);

  List<UserEntityRelation> listUserRelations(String userId);

  void createRelation(UserEntityRelation userEntityRelation);

  void deleteRelation(String userId, EntityRelationId edgeId);
}
