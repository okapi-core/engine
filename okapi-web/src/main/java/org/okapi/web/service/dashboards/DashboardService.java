/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import static org.okapi.data.model.EntityType.*;
import static org.okapi.validation.OkapiChecks.checkArgument;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.model.*;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.ids.UuidV7;
import org.okapi.web.auth.*;
import org.okapi.web.auth.tx.GrantOrgEditToDashboard;
import org.okapi.web.auth.tx.GrantOrgReadToDashboardTx;
import org.okapi.web.dtos.dashboards.CreateDashboardRequest;
import org.okapi.web.dtos.dashboards.GetDashboardResponse;
import org.okapi.web.dtos.dashboards.GetDashboardRowResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardRequest;
import org.okapi.web.service.*;
import org.okapi.web.service.context.DashboardAccessContext;
import org.okapi.web.service.context.DashboardRowAccessContext;
import org.okapi.web.service.dashboards.rows.DashboardRowId;
import org.okapi.web.service.dashboards.rows.DashboardRowService;
import org.springframework.stereotype.Service;

@Service
public class DashboardService
    extends AbstractValidatedCrudService<
        DashboardAccessContext,
        DashboardRequestContext,
        CreateDashboardRequest,
        UpdateDashboardRequest,
        GetDashboardResponse> {
  public DashboardService(
      DashboardsRequestValidator validationService,
      DashboardDao dashboardDao,
      TokenManager tokenManager,
      AccessManager accessManager,
      RelationGraphDao relationGraphDao,
      DashboardRowDao dashboardRowDao,
      DashboardRowService dashboardRowService,
      DashboardAccessValidator dashboardAccessValidator,
      DashboardVersionService dashboardVersionService,
      DashboardVersionDao dashboardVersionDao,
      UserEntityRelationsDao entityRelationsDao,
      UserDetailsManager userDetailsManager) {
    super(validationService);
    this.dashboardDao = dashboardDao;
    this.tokenManager = tokenManager;
    this.accessManager = accessManager;
    this.relationGraphDao = relationGraphDao;
    this.dashboardRowDao = dashboardRowDao;
    this.dashboardRowService = dashboardRowService;
    this.dashboardAccessValidator = dashboardAccessValidator;
    this.dashboardVersionService = dashboardVersionService;
    this.dashboardVersionDao = dashboardVersionDao;
    this.entityRelationsDao = entityRelationsDao;
    this.userDetailsManager = userDetailsManager;
  }

  DashboardDao dashboardDao;
  TokenManager tokenManager;
  AccessManager accessManager;
  RelationGraphDao relationGraphDao;
  DashboardRowDao dashboardRowDao;
  DashboardRowService dashboardRowService;
  DashboardAccessValidator dashboardAccessValidator;
  DashboardVersionService dashboardVersionService;
  DashboardVersionDao dashboardVersionDao;
  UserEntityRelationsDao entityRelationsDao;
  UserDetailsManager userDetailsManager;

  @Override
  public GetDashboardResponse createAfterValidation(
      DashboardRequestContext context, CreateDashboardRequest request)
      throws UnAuthorizedException, ResourceNotFoundException {
    var dashboardId = UUID.randomUUID().toString();
    var versionId = UuidV7.randomUuid().toString();
    var orgId = context.getOrgMemberContext().getOrgId();
    var userId = context.getOrgMemberContext().getUserId();
    var newDto =
        Dashboard.builder()
            .dashboardId(dashboardId)
            .orgId(orgId)
            .creator(userId)
            .title(request.getDescription())
            .lastEditor(userId)
            .desc(request.getTitle())
            .activeVersion(versionId)
            .build();
    dashboardDao.save(newDto);
    var versionMeta =
        DashboardVersion.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(versionId)
            .status("PUBLISHED")
            .createdAt(Instant.now().toEpochMilli())
            .createdBy(userId)
            .specHash(null)
            .note("Initial version")
            .build();
    dashboardVersionDao.save(versionMeta);
    var orgReadTx = new GrantOrgReadToDashboardTx(orgId, dashboardId);
    orgReadTx.doTx(relationGraphDao);

    // var orgEdit
    var orgEditTx = new GrantOrgEditToDashboard(orgId, dashboardId);
    orgEditTx.doTx(relationGraphDao);
    // Seed the dashboard with a sample row and panel.
    //    dashboardHydrator.hydrate(orgId, dashboardId, versionId);
    // get details of creator
    var creator = userDetailsManager.getUserPersonalName(userId);
    var lastEditor = userDetailsManager.getUserPersonalName(userId);
    var userRelations = getUserDashboardRelations(userId, dashboardId);
    return Mappers.mapDashboardDtoToResponse(newDto, creator, lastEditor, userRelations);
  }

  @Override
  public GetDashboardResponse readAfterValidation(DashboardRequestContext context)
      throws ResourceNotFoundException {
    // list rows for this dashboard and expand each via row service (includes panels)
    var dashboardDtoOptional =
        dashboardDao.get(context.getOrgMemberContext().getOrgId(), context.getDashboardId());
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    var dto = dashboardDtoOptional.get();
    var versionId = dto.getActiveVersion();
    var rows =
        dashboardRowDao.getAll(
            context.getOrgMemberContext().getOrgId(), context.getDashboardId(), versionId);
    List<GetDashboardRowResponse> rowResponses =
        rows.stream()
            .map(
                r -> {
                  var rowCtx =
                      new DashboardRowAccessContext(
                          context.getOrgMemberContext(),
                          new DashboardRowId(
                              ResourceIdCreator.createResourceId(
                                  context.getOrgMemberContext().getOrgId(),
                                  context.getDashboardId(),
                                  r.getRowId())),
                          dashboardDtoOptional.get(),
                          versionId);
                  return dashboardRowService.readAfterValidation(rowCtx);
                })
            .toList();
    var userId = context.getOrgMemberContext().getUserId();
    var dashboardId = context.getDashboardId();
    var creator = userDetailsManager.getUserPersonalName(dto.getCreator());
    var lastEditor = userDetailsManager.getUserPersonalName(dto.getLastEditor());
    var userRelations = getUserDashboardRelations(userId, dashboardId);
    var partial = Mappers.mapDashboardToPartial(dto, creator, lastEditor, userRelations);
    partial.rows(rowResponses);
    handleUserView(userId, dashboardId);
    return partial.build();
  }

  public GetDashboardResponse readVersion(String tempToken, String dashboardId, String versionId)
      throws Exception {
    var dashCtx = new DashboardAccessContext(tempToken, dashboardId);
    var orgCtx = dashboardAccessValidator.validateRead(dashCtx);
    var validatedCtx = new DashboardRequestContext(orgCtx, dashboardId);
    return readVersionAfterValidation(validatedCtx, versionId);
  }

  private GetDashboardResponse readVersionAfterValidation(
      DashboardRequestContext context, String versionId) throws ResourceNotFoundException {
    var dashboardDtoOptional =
        dashboardDao.get(context.getOrgMemberContext().getOrgId(), context.getDashboardId());
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    var dto = dashboardDtoOptional.get();
    var rows =
        dashboardRowDao.getAll(
            context.getOrgMemberContext().getOrgId(), context.getDashboardId(), versionId);
    List<GetDashboardRowResponse> rowResponses =
        rows.stream()
            .map(
                r -> {
                  var rowCtx =
                      new DashboardRowAccessContext(
                          context.getOrgMemberContext(),
                          new DashboardRowId(
                              ResourceIdCreator.createResourceId(
                                  context.getOrgMemberContext().getOrgId(),
                                  context.getDashboardId(),
                                  r.getRowId())),
                          dto,
                          versionId);
                  return dashboardRowService.readAfterValidation(rowCtx);
                })
            .toList();
    var userId = context.getOrgMemberContext().getUserId();
    var creator = userDetailsManager.getUserPersonalName(dto.getCreator());
    var lastEditor = userDetailsManager.getUserPersonalName(dto.getLastEditor());
    var userRelations = getUserDashboardRelations(userId, dto.getDashboardId());
    var partial = Mappers.mapDashboardToPartial(dto, creator, lastEditor, userRelations);
    partial.rows(rowResponses);
    handleUserView(userId, dto.getDashboardId());
    return partial.build();
  }

  protected List<UserEntityRelation> getUserDashboardRelations(String userId, String dashboardId) {
    var faveRelation =
        entityRelationsDao.getRelation(
            userId,
            new EntityRelationId(DASHBOARD, dashboardId, UserRelationType.DASHBOARD_FAVE));
    var lastViewedRelation =
        entityRelationsDao.getRelation(
            userId,
            new EntityRelationId(DASHBOARD, dashboardId, UserRelationType.DASHBOARD_LAST_VIEWED));
    return List.of(faveRelation, lastViewedRelation).stream()
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  protected void handleUserView(String userId, String dashboardId) {
    var relation =
        UserEntityRelation.builder()
            .userId(userId)
            .edgeId(
                new EntityRelationId(
                    DASHBOARD, dashboardId, UserRelationType.DASHBOARD_LAST_VIEWED))
            .edgeAttributes(
                EdgeAttributes.builder().timestamp(Instant.now().toEpochMilli()).build())
            .build();
    entityRelationsDao.createRelation(relation);
  }

  @Override
  public GetDashboardResponse updateAfterValidation(
      DashboardRequestContext context, UpdateDashboardRequest request) throws Exception {
    var userId = context.getOrgMemberContext().getUserId();
    var dashboardId = context.getDashboardId();
    var dashboardDtoOptional =
        dashboardDao.get(context.getOrgMemberContext().getOrgId(), context.getDashboardId());
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    var dto = dashboardDtoOptional.get();
    var wasUpdated = false;
    if (request.getTitle() != null) {
      dto.setTitle(request.getTitle());
      wasUpdated = true;
    }
    if (request.getDesc() != null) {
      dto.setDesc(request.getDesc());
      wasUpdated = true;
    }
    if (request.getRowIds() != null) {
      dto.setRowOrder(new ResourceOrder(request.getRowIds()));
      wasUpdated = true;
    }
    if (wasUpdated) {
      dto.setLastEditor(userId);
    }
    handleFav(userId, dashboardId, request.getIsFavorite());
    dashboardDao.save(dto);
    return this.readAfterValidation(context);
  }

  public void handleFav(String userId, String dashboardId, Boolean isFavorite) throws Exception {
    if (isFavorite == null) {
      return;
    }
    var relation =
        UserEntityRelation.builder()
            .userId(userId)
            .edgeId(new EntityRelationId(DASHBOARD, dashboardId, UserRelationType.DASHBOARD_FAVE))
            .build();
    if (isFavorite) {
      entityRelationsDao.createRelation(relation);
    } else {
      entityRelationsDao.deleteRelation(relation.getUserId(), relation.getEdgeId());
    }
  }

  @Override
  public void deleteAfterValidation(DashboardRequestContext context)
      throws ResourceNotFoundException {
    var orgId = context.getOrgMemberContext().getOrgId();
    var dashboardId = context.getDashboardId();
    var dashboardDtoOptional = dashboardDao.get(orgId, dashboardId);
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    dashboardDao.delete(dashboardId);
    relationGraphDao.deleteEntity(EntityId.of(DASHBOARD, dashboardId));
  }

  public List<GetDashboardResponse> listDashboards(String tempHeader) throws Exception {
    var userId = tokenManager.getUserId(tempHeader);
    var orgId = tokenManager.getOrgId(tempHeader);
    var dashboards = dashboardDao.getAll(orgId);
    var result =
        dashboards.stream()
            .filter(
                d -> {
                  try {
                    var canRead =
                        relationGraphDao.isAnyPathBetween(
                            EntityId.of(USER, userId),
                            EntityId.of(DASHBOARD, d.getDashboardId()),
                            PathWays.DASH_READ_PATH_WAY);
                    return canRead;
                  } catch (Exception e) {
                    return false;
                  }
                })
            .map(
                dashboardDdb -> {
                  var creator = userDetailsManager.getUserPersonalName(dashboardDdb.getCreator());
                  var lastEditor =
                      userDetailsManager.getUserPersonalName(dashboardDdb.getLastEditor());
                  var userDashboardRelations =
                      getUserDashboardRelations(userId, dashboardDdb.getDashboardId());
                  return Mappers.mapDashboardDtoToResponse(
                      dashboardDdb, creator, lastEditor, userDashboardRelations);
                })
            .toList();
    return result;
  }

  public org.okapi.web.dtos.dashboards.versions.ListDashboardVersionsResponse listVersions(
      String tempToken, String dashboardId) throws Exception {
    return dashboardVersionService.list(tempToken, dashboardId);
  }

  public org.okapi.web.dtos.dashboards.versions.PublishDashboardVersionResponse publishVersion(
      String tempToken, String dashboardId, String versionId) throws Exception {
    return dashboardVersionService.publish(tempToken, dashboardId, versionId);
  }
}
