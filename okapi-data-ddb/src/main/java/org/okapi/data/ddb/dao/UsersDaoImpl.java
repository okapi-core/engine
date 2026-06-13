/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.ddb.dao;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.*;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.ddb.iterators.FlatteningIterator;
import org.okapi.data.ddb.iterators.MappingIterator;
import org.okapi.data.dto.*;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.User;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

public class UsersDaoImpl implements UsersDao {

  DynamoDbEnhancedClient dynamoDbEnhancedClient;
  DynamoDbTable<UserDtoDdb> usersTable;

  @Inject
  public UsersDaoImpl(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
    this.dynamoDbEnhancedClient = dynamoDbEnhancedClient;
    usersTable =
        this.dynamoDbEnhancedClient.table(
            TablesAndIndexes.USERS_TABLE, TableSchema.fromBean(UserDtoDdb.class));
  }

  @Override
  public Optional<User> get(String userId) {
    var obj = usersTable.getItem(Key.builder().partitionValue(userId).build());
    return Optional.ofNullable(DdbMapper.toApi(obj));
  }

  @Override
  public Optional<User> getWithEmail(String email) {
    var idx = usersTable.index(TablesAndIndexes.USERS_BY_EMAIL_GSI);
    var query =
        idx.query(
            QueryEnhancedRequest.builder()
                .queryConditional(
                    QueryConditional.keyEqualTo(Key.builder().partitionValue(email).build()))
                .build());
    var lists = Lists.newArrayList(new FlatteningIterator<>(query.iterator()));
    if (lists.isEmpty()) return Optional.empty();
    else return Optional.of(DdbMapper.toApi(lists.getFirst()));
  }

  @Override
  public User createIfNotExists(
      String firstName, String lastName, String email, String password)
      throws UserAlreadyExistsException {
    var userId = UUID.randomUUID().toString();
    String hashed;
    if (password == null) {
      hashed = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt());
    } else {
      hashed = BCrypt.hashpw(password, BCrypt.gensalt());
    }
    var existingUser = getWithEmail(email);
    if (existingUser.isPresent()) {
      throw new UserAlreadyExistsException();
    }
    var user =
        UserDtoDdb.builder()
            .userId(userId)
            .status(UserDtoDdb.UserStatus.ACTIVE)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .hashedPassword(hashed)
            .build();
    usersTable.putItem(user);
    return DdbMapper.toApi(user);
  }

  @Override
  public Iterator<User> listAllUsers() {
    var scan = usersTable.scan();
    return new MappingIterator<>(new FlatteningIterator<>(scan.iterator()), DdbMapper::toApi);
  }

  @Override
  public void update(User user) {
    var obj = DdbMapper.toDdb(user);
    usersTable.updateItem(obj);
  }
}
