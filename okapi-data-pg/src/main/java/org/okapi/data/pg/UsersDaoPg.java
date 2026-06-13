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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class UsersDaoPg implements UsersDao {
  private final JdbcTemplate jdbc;

  public UsersDaoPg(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<User> get(String id) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE user_id = ?",
            (rs, row) ->
                User.builder()
                    .userId(rs.getString("user_id"))
                    .email(rs.getString("email"))
                    .status(User.Status.valueOf(rs.getString("status")))
                    .firstName(rs.getString("first_name"))
                    .lastName(rs.getString("last_name"))
                    .hashedPassword(rs.getString("hashed_password"))
                    .build(),
            id)
        .stream()
        .findFirst();
  }

  public Optional<User> getWithEmail(String email) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE lower(email) = lower(?)",
            (rs, row) ->
                User.builder()
                    .userId(rs.getString("user_id"))
                    .email(rs.getString("email"))
                    .status(User.Status.valueOf(rs.getString("status")))
                    .firstName(rs.getString("first_name"))
                    .lastName(rs.getString("last_name"))
                    .hashedPassword(rs.getString("hashed_password"))
                    .build(),
            email)
        .stream()
        .findFirst();
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
    } catch (DuplicateKeyException e) {
      throw new UserAlreadyExistsException();
    }
    return user;
  }

  public Iterator<User> listAllUsers() {
    return jdbc.query(
            "SELECT * FROM users ORDER BY user_id",
            (rs, row) ->
                User.builder()
                    .userId(rs.getString("user_id"))
                    .email(rs.getString("email"))
                    .status(User.Status.valueOf(rs.getString("status")))
                    .firstName(rs.getString("first_name"))
                    .lastName(rs.getString("last_name"))
                    .hashedPassword(rs.getString("hashed_password"))
                    .build())
        .iterator();
  }

  public void update(User user) {
    jdbc.update(
        """
        INSERT INTO users (user_id, email, status, first_name, last_name, hashed_password)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
          email = EXCLUDED.email,
          status = EXCLUDED.status,
          first_name = EXCLUDED.first_name,
          last_name = EXCLUDED.last_name,
          hashed_password = EXCLUDED.hashed_password
        """,
        user.getUserId(),
        user.getEmail(),
        user.getStatus().name(),
        user.getFirstName(),
        user.getLastName(),
        user.getHashedPassword());
  }
}
