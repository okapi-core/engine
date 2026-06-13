/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.User;

public final class UsersDaoPg implements UsersDao {
  private final JdbcRecordStore store;

  public UsersDaoPg(JdbcRecordStore store) {
    this.store = store;
  }

  public Optional<User> get(String id) {
    return store.get("user", id, User.class);
  }

  public Optional<User> getWithEmail(String email) {
    return store.findByScope("user", email, User.class);
  }

  public User createIfNotExists(String first, String last, String email, String password)
      throws UserAlreadyExistsException {
    if (getWithEmail(email).isPresent()) throw new UserAlreadyExistsException();
    var hash =
        BCrypt.hashpw(password == null ? UUID.randomUUID().toString() : password, BCrypt.gensalt());
    var user =
        User.builder()
            .userId(UUID.randomUUID().toString())
            .email(email)
            .status(User.Status.ACTIVE)
            .firstName(first)
            .lastName(last)
            .hashedPassword(hash)
            .build();
    try {
      update(user);
    } catch (RuntimeException e) {
      if (getWithEmail(email).isPresent()) throw new UserAlreadyExistsException();
      throw e;
    }
    return user;
  }

  public Iterator<User> listAllUsers() {
    return store.list("user", null, null, User.class).iterator();
  }

  public void update(User user) {
    store.put("user", user.getUserId(), user.getEmail(), null, user.getStatus().name(), null, user);
  }
}
