/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.data.dao.RelationGraphDao.makeRelation;
import static org.okapi.data.model.EntityType.*;
import static org.okapi.data.model.RelationType.*;
import static org.okapi.data.model.EntityId.of;
import static org.okapi.fixtures.Deduplicator.dedup;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EdgeSequence;
import org.okapi.data.model.EntityId;
import org.okapi.data.ddb.dao.RelationGraphDaoImpl;
import org.okapi.data.model.RelationGraphNode;
import org.okapi.data.migrations.RelationGraphDdbSpec;
import org.okapi.testutils.OkapiTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class RelationGraphDaoImplTests {

  RelationGraphDao dao;
  DynamoDbClient ddbClient = OkapiTestUtils.getLocalStackDynamoDbClient();
  DynamoDbEnhancedClient enhancedClient =
      DynamoDbEnhancedClient.builder().dynamoDbClient(ddbClient).build();

  String testId = OkapiTestUtils.getTestId(RelationGraphDaoImplTests.class);
  String userA = dedup(testId, "userA");
  String orgA = dedup(testId, "orgA");
  String dashRead = dedup(testId, "dashA");
  String dashEdit = dedup(testId, "dashB");
  String userB = dedup(testId, "userB");
  String orgB = dedup(testId, "orgB");
  String dashB = dedup(testId, "dashB");

  String globalUser = dedup(testId, "globalUser");

  // list of accepted paths for dash edit and read
  List<EdgeSequence> dashEditRoute =
      List.of(
          new EdgeSequence(
              Arrays.asList(
                  makeRelation(ORG, ORG_MEMBER), makeRelation(DASHBOARD, DASHBOARD_EDIT))));

  List<EdgeSequence> dashReadRoute =
      List.of(
          new EdgeSequence(
              Arrays.asList(
                  makeRelation(ORG, ORG_MEMBER), makeRelation(DASHBOARD, DASHBOARD_READ))));

  List<EdgeSequence> orgMemberRoute = List.of(new EdgeSequence(Arrays.asList(makeRelation(ORG, ORG_MEMBER))));

  @BeforeEach
  public void setup() {
    var spec = new RelationGraphDdbSpec();
    spec.create(ddbClient);
    dao = new RelationGraphDaoImpl(enhancedClient);
    // A is realted to orgA
    dao.addRelationship(new EntityId(USER, userA), of(ORG, orgA), ORG_MEMBER);

    // B is related to orgB
    dao.addRelationship(of(USER, userB), of(ORG, orgB), ORG_MEMBER);

    // anyone in orgA has read access to dashA and edit access to dashB
    dao.addRelationship(of(ORG, orgA), of(DASHBOARD, dashRead), DASHBOARD_READ);
    dao.addRelationship(of(ORG, orgA), of(DASHBOARD, dashEdit), DASHBOARD_EDIT);

    // dashB is only accessible by orgB members with read access
    dao.addRelationship(of(ORG, orgB), of(DASHBOARD, dashB), DASHBOARD_READ);

    // globalUser is member of orgA and orgB
    dao.addRelationship(new EntityId(USER, globalUser), of(ORG, orgA), ORG_MEMBER);
    dao.addRelationship(new EntityId(USER, globalUser), of(ORG, orgB), ORG_MEMBER);
  }

  @Test
  public void testRelations() {
    // single paths
    assertTrue(
        dao.isAnyPathBetween(of(USER, userA), of(ORG, orgA), orgMemberRoute));

    // any path
    assertTrue(
        dao.isAnyPathBetween(
            of(USER, globalUser), of(DASHBOARD, dashRead), dashReadRoute));

    assertTrue(
        dao.isAnyPathBetween(
            of(USER, globalUser), of(DASHBOARD, dashEdit), dashEditRoute));

    // global user can also access dashB with read access
    assertTrue(
        dao.isAnyPathBetween(
            of(USER, globalUser), of(DASHBOARD, dashB), dashReadRoute));

    // negative tests
    assertFalse(
        dao.isAnyPathBetween(
            of(USER, userB), of(DASHBOARD, dashRead), dashReadRoute));
    assertTrue(
        dao.hasRelationBetween(of(USER, userA), of(ORG, orgA), ORG_MEMBER));
    assertFalse(
        dao.hasRelationBetween(of(USER, userA), of(ORG, orgB), ORG_MEMBER));

    // get all relations of type
    var relations = dao.getAllRelationsOfNodeType(EntityId.of(USER, globalUser), ORG);
    assertEquals(2, relations.size());

    var orgIds = relations.stream().map(RelationGraphNode::getRelatedEntity).toList();
    assertTrue(orgIds.contains(EntityId.of(ORG, orgA).toString()));
    assertTrue(orgIds.contains(EntityId.of(ORG, orgB).toString()));

    // delete
    dao.deleteEntity(EntityId.of(USER, userA));
    assertFalse(
        dao.isAnyPathBetween(
            of(USER, userA), of(DASHBOARD, dashRead), dashReadRoute));
    dao.deleteEntity(EntityId.of(ORG, orgB));
    assertFalse(
        dao.isAnyPathBetween(
            of(USER, globalUser), of(DASHBOARD, dashB), dashReadRoute));
  }
}
