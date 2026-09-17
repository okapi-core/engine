/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.data.model.RelationType.ORG_ADMIN;
import static org.okapi.data.model.RelationType.ORG_MEMBER;

import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityId;
import org.okapi.exceptions.NotFoundException;
import org.okapi.exceptions.UnAuthorizedException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AccessManager {

  private final OrgDao orgDao;
  private final RelationGraphDao relationGraphDao;

  public void checkOrgMember(String userId, String orgId) {
    checkOrganizationExists(orgId);
    var user = EntityId.of(USER, userId);
    var org = EntityId.of(ORG, orgId);
    if (!relationGraphDao.hasRelationBetween(user, org, ORG_MEMBER)
        && !relationGraphDao.hasRelationBetween(user, org, ORG_ADMIN)) {
      throw new UnAuthorizedException("User is not a member of this organization.");
    }
  }

  public void checkOrgAdmin(String userId, String orgId) {
    checkOrganizationExists(orgId);
    if (!relationGraphDao.hasRelationBetween(
        EntityId.of(USER, userId), EntityId.of(ORG, orgId), ORG_ADMIN)) {
      throw new UnAuthorizedException("User is not an administrator of this organization.");
    }
  }

  private void checkOrganizationExists(String orgId) {
    if (orgDao.findById(orgId).isEmpty()) {
      throw new NotFoundException("Organization not found: " + orgId);
    }
  }
}
