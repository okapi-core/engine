/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import org.okapi.data.pg.entity.UserEntityRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEntityRelationRepository
    extends JpaRepository<UserEntityRelationEntity, UserEntityRelationEntity.Id> {
  List<UserEntityRelationEntity>
      findAllByIdUserIdOrderByIdEntityTypeAscIdEntityIdAscIdRelationTypeAsc(String userId);
}
