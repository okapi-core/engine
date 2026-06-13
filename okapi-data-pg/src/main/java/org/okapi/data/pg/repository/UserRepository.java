/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg.repository;

import java.util.List;
import java.util.Optional;
import org.okapi.data.pg.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
  Optional<UserEntity> findByEmailIgnoreCase(String email);

  List<UserEntity> findAllByOrderByUserId();
}
