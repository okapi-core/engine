/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.dao;

import java.util.List;
import java.util.Optional;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.User;

public interface UsersDao {
  Optional<User> get(String userId);

  Optional<User> getWithEmail(String email);

  User createIfNotExists(
      String firstName, String lastName, String email, String password, String orgId)
      throws UserAlreadyExistsException;

  void update(User user);

  List<User> getAll(String orgId);
}
