/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth.tx;

import static org.okapi.data.model.EntityType.ORG;

import lombok.AllArgsConstructor;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityType;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.RelationType;
import org.okapi.web.auth.GraphTx;

@AllArgsConstructor
public class AddMemberToOrgTx implements GraphTx {
  String userId;
  String orgId;

  @Override
  public void doTx(RelationGraphDao relationGraphDao) {
    relationGraphDao.addRelationship(
        EntityId.of(EntityType.USER, userId), EntityId.of(ORG, orgId), RelationType.ORG_MEMBER);

    // inverse relation
    relationGraphDao.addRelationship(
        EntityId.of(ORG, orgId), EntityId.of(EntityType.USER, userId), RelationType.ORG_MEMBER);
  }
}
