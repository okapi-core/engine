/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.*;

import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.CreateDynamoDBTables;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.model.EntityType;
import org.okapi.data.model.EdgeAttributes;
import org.okapi.data.model.EntityRelationId;
import org.okapi.data.model.UserRelationType;
import org.okapi.data.ddb.dao.UserEntityRelationsDaoImpl;
import org.okapi.testutils.OkapiTestUtils;

public class UserEntityRelationsDaoImplTest {

  private UserEntityRelationsDao dao;
  private String userId;
  private EntityRelationId relationId;
  Injector injector;

  @BeforeEach
  public void setup() {
    // Ensure all required tables exist in localstack
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    injector = Injectors.createTestInjector();
    dao = injector.getInstance(UserEntityRelationsDaoImpl.class);
    userId = OkapiTestUtils.getTestId(getClass());
    var entityId = OkapiTestUtils.getTestId(getClass());
    relationId =
        new EntityRelationId(EntityType.DASHBOARD, entityId, UserRelationType.DASHBOARD_FAVE);
  }

  @Test
  public void lifecycle_create_list_get_delete() {
    // 1) Create
    var attrs = new EdgeAttributes(System.currentTimeMillis(), "fav", true);
    var rel =
        org.okapi.data.model.UserEntityRelation.builder()
            .userId(userId)
            .edgeId(relationId)
            .edgeAttributes(attrs)
            .build();
    dao.createRelation(rel);

    // 2) List and verify presence
    var listed = dao.listUserRelations(userId);
    assertNotNull(listed);
    assertTrue(listed.size() >= 1);

    // 3) Get by key and verify present
    var fetched = dao.getRelation(userId, relationId);
    assertTrue(fetched.isPresent());

    // 4) Delete
    dao.deleteRelation(userId, relationId);

    // 5) List again and ensure it is gone
    var afterDelete = dao.listUserRelations(userId);
    assertEquals(0, afterDelete.size());

    // 6) Get by key should be empty
    var fetchedAfterDelete = dao.getRelation(userId, relationId);
    assertTrue(fetchedAfterDelete.isEmpty());
  }
}
