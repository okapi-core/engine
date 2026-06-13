/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.validation.OkapiChecks.checkArgument;

import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.RelationType;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.NotFoundException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.usermessages.UserFacingMessages;
import org.okapi.web.auth.tx.AddMemberToOrgTx;
import org.okapi.web.auth.tx.MakeUserOrgAdmin;
import org.okapi.web.dtos.org.*;
import org.okapi.web.dtos.users.GetOrgUserView;
import org.okapi.web.dtos.users.ORG_ROLE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrgManager {

  TokenManager tokenManager;
  AccessManager accessManager;
  UsersDao usersDao;
  OrgDao orgDao;
  RelationGraphDao relationGraphDao;

  @Autowired
  public OrgManager(
      TokenManager tokenManager,
      AccessManager accessManager,
      UsersDao usersDao,
      OrgDao orgDao,
      RelationGraphDao relationGraphDao) {
    this.tokenManager = tokenManager;
    this.accessManager = accessManager;
    this.usersDao = usersDao;
    this.orgDao = orgDao;
    this.relationGraphDao = relationGraphDao;
  }

  public void createOrgMember(String tempToken, CreateOrgMemberRequest request)
      throws UnAuthorizedException {
    var userId = tokenManager.getUserId(tempToken);
    var orgId = request.getOrgId();
    accessManager.checkUserHasIsOrgAdmin(userId, orgId);
    var memberUser = usersDao.getWithEmail(request.getEmail());
    if (memberUser.isEmpty()) {
      throw new UnAuthorizedException(UserFacingMessages.USER_NOT_FOUND);
    }
    var memberToAdd = memberUser.get();
    // member role is always granted
    var addOrgTx = new AddMemberToOrgTx(memberToAdd.getUserId(), orgId);
    addOrgTx.doTx(relationGraphDao);

    if (request.isAdmin()) {
      // if the request allows for an admin, the user is optionally granted an Admin role.
      var addAdminTx = new MakeUserOrgAdmin(memberToAdd.getUserId(), orgId);
      addAdminTx.doTx(relationGraphDao);
    }
  }

  public void updateOrgMember(String tempToken, String orgId, UpdateOrgMemberRequest request)
      throws UnAuthorizedException, NotFoundException, BadRequestException {
    var operator = tokenManager.getUserId(tempToken);
    var memberEmail = request.getEmail();
    accessManager.checkUserHasIsOrgAdmin(operator, orgId);
    var org = orgDao.findById(orgId);
    checkArgument(org.isPresent(), NotFoundException::new);
    var memberUser = usersDao.getWithEmail(memberEmail);
    checkArgument(
        memberUser.isPresent(), () -> new BadRequestException(UserFacingMessages.USER_NOT_FOUND));
    checkArgument(
        !org.get().getOrgCreator().equals(memberUser.get().getUserId()),
        () -> new BadRequestException(UserFacingMessages.CANT_REMOVE_SELF));

    var memberId = memberUser.get().getUserId();
    relationGraphDao.removeAllRelations(EntityId.of(USER, memberId), EntityId.of(ORG, orgId));

    if (request.getRoles().contains(ORG_ROLE.MEMBER)) {
      var addMemberTx = new AddMemberToOrgTx(memberId, orgId);
      addMemberTx.doTx(relationGraphDao);
    } else if (request.getRoles().contains(ORG_ROLE.ADMIN)) {
      var addAdminTx = new MakeUserOrgAdmin(memberId, orgId);
      addAdminTx.doTx(relationGraphDao);
      var addMemberTx = new AddMemberToOrgTx(memberId, orgId);
      addMemberTx.doTx(relationGraphDao);
    }
  }

  public GetOrgResponse getOrg(String tempToken, String orgId) throws UnAuthorizedException {
    var userId = tokenManager.getUserId(tempToken);
    accessManager.checkUserHasIsOrgMember(userId, orgId);
    List<OrgMemberWDto> members =
        relationGraphDao
            .getAllRelationsOfType(EntityId.of(ORG, orgId), USER, RelationType.ORG_MEMBER)
            .stream()
            .map(
                entityId -> {
                  var relatedEntityId = entityId.getRelatedEntity();
                  var parsedId = EntityId.parse(relatedEntityId);
                  if (parsedId.isEmpty()) {
                    throw new RuntimeException(
                        "Inconsistent data: invalid user id: " + relatedEntityId);
                  }
                  var maybeUser = usersDao.get(parsedId.get().id());
                  if (maybeUser.isEmpty()) {
                    throw new RuntimeException(
                        "Inconsistent data: user not found: " + relatedEntityId);
                  }
                  var userDto = maybeUser.get();
                  var isAlsoAdmin =
                      relationGraphDao.hasRelationBetween(
                          EntityId.of(ORG, orgId),
                          EntityId.of(USER, parsedId.get().id()),
                          RelationType.ORG_ADMIN);
                  return new OrgMemberWDto(
                      userDto.getUserId(),
                      userDto.getFirstName(),
                      userDto.getLastName(),
                      userDto.getEmail(),
                      isAlsoAdmin);
                })
            .collect(Collectors.toList());
    var maybeOrg =
        orgDao
            .findById(orgId)
            .map(dto -> new GetOrgResponse(dto.getOrgId(), dto.getOrgName(), members))
            .orElseThrow(() -> new UnAuthorizedException(UserFacingMessages.ORG_NOT_FOUND));
    return maybeOrg;
  }

  // list all orgs that a user is a member of
  public ListOrgsResponse listOrgs(String loginToken) throws UnAuthorizedException {
    var userId = tokenManager.getUserId(loginToken);
    var orgRelations = relationGraphDao.getAllRelationsOfNodeType(EntityId.of(USER, userId), ORG);
    List<GetOrgUserView> orgs = new ArrayList<>();
    for (var relation : orgRelations) {
      var orgEntityId = relation.getRelatedEntity();
      var orgId = EntityId.parse(orgEntityId);
      if (orgId.isEmpty()) {
        continue;
      }
      var maybeOrgDto = orgDao.findById(orgId.get().id());
      if (maybeOrgDto.isEmpty()) {
        throw new RuntimeException("Inconsistent data: org not found: " + orgId);
      }
      var orgDto = maybeOrgDto.get();
      var relationType = relation.getRelationships();
      var isAdmin = relationType.contains(RelationType.ORG_ADMIN);
      var isMember = relationType.contains(RelationType.ORG_MEMBER);
      var roles = new ArrayList<ORG_ROLE>();
      if (isAdmin) {
        roles.add(ORG_ROLE.ADMIN);
      }
      if (isMember) {
        roles.add(ORG_ROLE.MEMBER);
      }

      orgs.add(new GetOrgUserView(orgDto.getOrgId(), orgDto.getOrgName(), roles));
    }
    return ListOrgsResponse.builder().orgs(orgs).build();
  }

  public GetOrgResponse updateOrg(String tempToken, String orgId, UpdateOrgRequest request)
      throws UnAuthorizedException, BadRequestException {
    var userId = tokenManager.getUserId(tempToken);
    accessManager.checkUserHasIsOrgAdmin(userId, orgId);
    var maybeOrgDto = orgDao.findById(orgId);
    // check that org exists
    maybeOrgDto.orElseThrow(() -> new BadRequestException(UserFacingMessages.ORG_NOT_FOUND));
    var orgDto = maybeOrgDto.get();
    orgDto.setOrgName(request.getOrgName());
    orgDao.save(orgDto);
    return GetOrgResponse.builder().orgId(orgId).orgName(orgDto.getOrgName()).build();
  }
}
