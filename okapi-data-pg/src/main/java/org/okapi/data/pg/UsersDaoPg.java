/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.User;
import org.okapi.data.pg.entity.UserEntity;
import org.okapi.data.pg.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;

public final class UsersDaoPg implements UsersDao {
  private final UserRepository repository;

  public UsersDaoPg(UserRepository repository) {
    this.repository = repository;
  }

  public Optional<User> get(String id) {
    return repository.findById(id).map(this::toDto);
  }

  public Optional<User> getWithEmail(String email) {
    return repository.findByEmailIgnoreCase(email).map(this::toDto);
  }

  public User createIfNotExists(
      String first, String last, String email, String password, String orgId)
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
            .orgId(orgId)
            .build();
    try {
      repository.saveAndFlush(toEntity(user));
    } catch (DataIntegrityViolationException e) {
      throw new UserAlreadyExistsException();
    }
    return user;
  }

  public void update(User user) {
    repository.saveAndFlush(toEntity(user));
  }

  @Override
  public List<User> getAll(String orgId) {
    return repository.findAllByOrgId(orgId).stream().map(this::toDto).toList();
  }

  private UserEntity toEntity(User user) {
    return new UserEntity(
        user.getUserId(),
        user.getEmail(),
        user.getStatus(),
        user.getFirstName(),
        user.getLastName(),
        user.getHashedPassword(),
        user.getOrgId());
  }

  private User toDto(UserEntity entity) {
    return User.builder()
        .userId(entity.getUserId())
        .email(entity.getEmail())
        .status(entity.getStatus())
        .firstName(entity.getFirstName())
        .lastName(entity.getLastName())
        .hashedPassword(entity.getHashedPassword())
        .orgId(entity.getOrgId())
        .build();
  }
}
