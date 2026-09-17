/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.orgs;

import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.data.model.RelationType.ORG_ADMIN;
import static org.okapi.data.model.RelationType.ORG_MEMBER;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.Organization;
import org.okapi.exceptions.NotFoundException;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.dtos.org.CreateOrgRequest;
import org.okapi.web.dtos.org.GetOrgResponse;
import org.okapi.web.dtos.org.OrgMemberWDto;
import org.okapi.web.security.CurrentUserProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrgService {
  private final OrgDao orgDao;
  private final UsersDao usersDao;
  private final RelationGraphDao relationGraphDao;
  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;

  public GetOrgResponse create(CreateOrgRequest request) {
    var userId = currentUserProvider.userId();
    var organization =
        Organization.builder()
            .orgId(UUID.randomUUID().toString())
            .orgName(request.getOrgName())
            .orgCreator(userId)
            .created(Instant.now())
            .build();
    orgDao.save(organization);
    relationGraphDao.addAllRelationships(
        EntityId.of(USER, userId),
        EntityId.of(ORG, organization.getOrgId()),
        List.of(ORG_MEMBER, ORG_ADMIN));
    return toResponse(organization);
  }

  public GetOrgResponse get(String orgId) {
    accessManager.checkOrgMember(currentUserProvider.userId(), orgId);
    return toResponse(orgDao.findById(orgId).orElseThrow(NotFoundException::new));
  }

  private GetOrgResponse toResponse(Organization org) {
    return GetOrgResponse.builder()
        .orgId(org.getOrgId())
        .orgName(org.getOrgName())
        .members(listMembers(org.getOrgId()))
        .build();
  }

  private List<OrgMemberWDto> listMembers(String orgId) {
    var org = EntityId.of(ORG, orgId);
    var memberIds = new LinkedHashSet<String>();
    relationGraphDao.getAllIncomingRelations(org, USER, ORG_MEMBER).stream()
        .map(OrgService::relatedId)
        .forEach(memberIds::add);
    relationGraphDao.getAllIncomingRelations(org, USER, ORG_ADMIN).stream()
        .map(OrgService::relatedId)
        .forEach(memberIds::add);
    return memberIds.stream()
        .map(usersDao::get)
        .flatMap(java.util.Optional::stream)
        .map(
            user ->
                OrgMemberWDto.builder()
                    .userId(user.getUserId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .isAdmin(
                        relationGraphDao.hasRelationBetween(
                            EntityId.of(USER, user.getUserId()), org, ORG_ADMIN))
                    .build())
        .toList();
  }

  private static String relatedId(org.okapi.data.model.RelationGraphNode relation) {
    return EntityId.parse(relation.getRelatedEntity()).orElseThrow().id();
  }
}
