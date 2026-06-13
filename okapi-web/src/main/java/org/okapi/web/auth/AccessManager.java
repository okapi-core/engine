/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityType;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.RelationType;
import org.okapi.exceptions.UnAuthorizedException;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class AccessManager {

  RelationGraphDao relationGraphDao;

  @Builder
  public record AuthContext(String userId, String orgId) {}

  public void checkUserHasIsOrgAdmin(String userId, String org) throws UnAuthorizedException {
    var isAdmin =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, userId),
            EntityId.of(EntityType.ORG, org),
            RelationType.ORG_ADMIN);
    if (!isAdmin) throw new UnAuthorizedException("User is not an org admin");
  }

  public void checkUserHasIsOrgMember(String userId, String org) throws UnAuthorizedException {
    var isAdmin =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, userId),
            EntityId.of(EntityType.ORG, org),
            RelationType.ORG_ADMIN);
    var isMember =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, userId),
            EntityId.of(EntityType.ORG, org),
            RelationType.ORG_MEMBER);
    if (!isAdmin && !isMember) throw new UnAuthorizedException("User is not in org.");
  }

  public AuthContext checkUserIsOrgAdmin(AuthContext authContext) {
    var isAdmin =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, authContext.userId()),
            EntityId.of(EntityType.ORG, authContext.orgId()),
            RelationType.ORG_ADMIN);
    if (!isAdmin) throw new UnAuthorizedException("User is not an org admin");
    return authContext;
  }

  public void checkUserIsOrgMember(String userId, String orgId) {
    var isAdmin =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, userId),
            EntityId.of(EntityType.ORG, orgId),
            RelationType.ORG_ADMIN);
    var isMember =
        relationGraphDao.hasRelationBetween(
            EntityId.of(EntityType.USER, userId),
            EntityId.of(EntityType.ORG, orgId),
            RelationType.ORG_MEMBER);
    if (!isAdmin && !isMember) throw new UnAuthorizedException("User is not in org.");
  }

  public AuthContext checkUserIsOrgMember(AuthContext authContext) {
    checkUserIsOrgMember(authContext.userId, authContext.orgId);
    return authContext;
  }
}
