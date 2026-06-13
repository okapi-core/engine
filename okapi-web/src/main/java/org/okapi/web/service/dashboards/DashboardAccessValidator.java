/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import static org.okapi.data.model.EntityType.DASHBOARD;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.validation.OkapiChecks.checkArgument;

import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.web.auth.PathWays;
import org.okapi.web.service.context.DashboardAccessContext;
import org.okapi.web.service.context.OrgMemberContext;
import org.okapi.web.service.validation.OrgMemberValidator;
import org.springframework.stereotype.Service;

@Service
public class DashboardAccessValidator {
  OrgMemberValidator orgMemberValidator;
  RelationGraphDao relationGraphDao;

  public DashboardAccessValidator(
      OrgMemberValidator orgMemberValidator, RelationGraphDao relationGraphDao) {
    this.orgMemberValidator = orgMemberValidator;
    this.relationGraphDao = relationGraphDao;
  }

  public OrgMemberContext validateEdit(DashboardAccessContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var orgMemberCtx = orgMemberValidator.checkOrgMember(context.getToken());
    var canRead =
        relationGraphDao.isAnyPathBetween(
            EntityId.of(USER, orgMemberCtx.getUserId()),
            EntityId.of(DASHBOARD, context.getDashboardId()),
            PathWays.DASH_EDIT_PATH_WAY);
    checkArgument(canRead, UnAuthorizedException::new);
    return orgMemberCtx;
  }

  public OrgMemberContext validateRead(DashboardAccessContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    var orgMemberCtx = orgMemberValidator.checkOrgMember(context.getToken());
    var canRead =
        relationGraphDao.isAnyPathBetween(
            EntityId.of(USER, orgMemberCtx.getUserId()),
            EntityId.of(DASHBOARD, context.getDashboardId()),
            PathWays.DASH_READ_PATH_WAY);
    checkArgument(canRead, UnAuthorizedException::new);
    return orgMemberCtx;
  }
}
