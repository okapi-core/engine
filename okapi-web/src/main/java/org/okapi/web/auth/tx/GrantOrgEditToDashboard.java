/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth.tx;

import static org.okapi.data.model.EntityType.DASHBOARD;
import static org.okapi.data.model.EntityType.ORG;

import lombok.AllArgsConstructor;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.RelationType;
import org.okapi.web.auth.GraphTx;

@AllArgsConstructor
public class GrantOrgEditToDashboard implements GraphTx {
  String orgId;
  String dashboardId;

  @Override
  public void doTx(RelationGraphDao relationGraphDao) {
    relationGraphDao.addRelationship(
        EntityId.of(ORG, orgId), EntityId.of(DASHBOARD, dashboardId), RelationType.DASHBOARD_EDIT);
  }
}
