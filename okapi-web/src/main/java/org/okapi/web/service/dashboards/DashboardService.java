/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import static org.okapi.data.model.EntityType.DASHBOARD;
import static org.okapi.validation.OkapiChecks.checkArgument;

import java.time.Instant;
import java.util.*;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.dao.UserEntityRelationsDao;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.*;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.ids.UuidV7;
import org.okapi.web.auth.UserDetailsManager;
import org.okapi.web.dtos.dashboards.CreateDashboardRequest;
import org.okapi.web.dtos.dashboards.GetDashboardResponse;
import org.okapi.web.dtos.dashboards.GetDashboardRowResponse;
import org.okapi.web.dtos.dashboards.UpdateDashboardRequest;
import org.okapi.web.dtos.dashboards.versions.ListDashboardVersionsResponse;
import org.okapi.web.dtos.dashboards.versions.PublishDashboardVersionResponse;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.Mappers;
import org.okapi.web.service.context.DashboardRequestContext;
import org.okapi.web.service.context.DashboardRowRequestContext;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.okapi.web.service.context.OrgRequestContext;
import org.okapi.web.service.dashboards.rows.DashboardRowService;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  public DashboardService(
      DashboardsRequestValidator validationService,
      DashboardDao dashboardDao,
      CurrentUserProvider currentUserProvider,
      DashboardRowDao dashboardRowDao,
      DashboardRowService dashboardRowService,
      DashboardVersionService dashboardVersionService,
      DashboardVersionDao dashboardVersionDao,
      UserEntityRelationsDao entityRelationsDao,
      UserDetailsManager userDetailsManager) {
    this.validator = validationService;
    this.dashboardDao = dashboardDao;
    this.currentUserProvider = currentUserProvider;
    this.dashboardRowDao = dashboardRowDao;
    this.dashboardRowService = dashboardRowService;
    this.dashboardVersionService = dashboardVersionService;
    this.dashboardVersionDao = dashboardVersionDao;
    this.entityRelationsDao = entityRelationsDao;
    this.userDetailsManager = userDetailsManager;
  }

  DashboardsRequestValidator validator;
  DashboardDao dashboardDao;
  CurrentUserProvider currentUserProvider;
  DashboardRowDao dashboardRowDao;
  DashboardRowService dashboardRowService;
  DashboardVersionService dashboardVersionService;
  DashboardVersionDao dashboardVersionDao;
  UserEntityRelationsDao entityRelationsDao;
  UserDetailsManager userDetailsManager;

  public GetDashboardResponse create(OrgRequestContext context, CreateDashboardRequest request)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    validator.validateCreate(context, request);
    return create(context.orgId(), currentUserProvider.userId(), request);
  }

  private GetDashboardResponse create(String orgId, String userId, CreateDashboardRequest request)
      throws UnAuthorizedException, ResourceNotFoundException {
    var dashboardId = UUID.randomUUID().toString();
    var versionId = UuidV7.randomUuid().toString();
    var newDto =
        Dashboard.builder()
            .dashboardId(dashboardId)
            .orgId(orgId)
            .creator(userId)
            .title(request.getTitle())
            .lastEditor(userId)
            .desc(request.getDescription())
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
    // Seed the dashboard with a sample row and panel.
    //    dashboardHydrator.hydrate(orgId, dashboardId, versionId);
    // get details of creator
    var creator = userDetailsManager.getUserPersonalName(userId);
    var lastEditor = userDetailsManager.getUserPersonalName(userId);
    var userRelations = getUserDashboardRelations(userId, dashboardId);
    return Mappers.mapDashboardDtoToResponse(newDto, creator, lastEditor, userRelations);
  }

  public GetDashboardResponse read(DashboardRequestContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    validator.validateRead(context);
    return read(context.orgId(), currentUserProvider.userId(), context.dashboardId());
  }

  private GetDashboardResponse read(String orgId, String userId, String dashboardId)
      throws ResourceNotFoundException {
    // list rows for this dashboard and expand each via row service (includes panels)
    var dashboardDtoOptional = dashboardDao.get(orgId, dashboardId);
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    var dto = dashboardDtoOptional.get();
    var versionId = dto.getActiveVersion();
    var rowResponses = readRows(orgId, dashboardId, versionId, dto);
    var creator = userDetailsManager.getUserPersonalName(dto.getCreator());
    var lastEditor = userDetailsManager.getUserPersonalName(dto.getLastEditor());
    var userRelations = getUserDashboardRelations(userId, dashboardId);
    var partial = Mappers.mapDashboardToPartial(dto, creator, lastEditor, userRelations);
    partial.rows(rowResponses);
    handleUserView(userId, dashboardId);
    return partial.build();
  }

  public GetDashboardResponse readVersion(DashboardVersionRequestContext context) throws Exception {
    validator.validateRead(new DashboardRequestContext(context.orgId(), context.dashboardId()));
    return readVersion(
        context.orgId(), currentUserProvider.userId(), context.dashboardId(), context.versionId());
  }

  private GetDashboardResponse readVersion(
      String orgId, String userId, String dashboardId, String versionId)
      throws ResourceNotFoundException {
    var dashboardDtoOptional = dashboardDao.get(orgId, dashboardId);
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    var dto = dashboardDtoOptional.get();
    var rowResponses = readRows(orgId, dashboardId, versionId, dto);
    var creator = userDetailsManager.getUserPersonalName(dto.getCreator());
    var lastEditor = userDetailsManager.getUserPersonalName(dto.getLastEditor());
    var userRelations = getUserDashboardRelations(userId, dto.getDashboardId());
    var partial = Mappers.mapDashboardToPartial(dto, creator, lastEditor, userRelations);
    partial.rows(rowResponses);
    handleUserView(userId, dto.getDashboardId());
    return partial.build();
  }

  private List<GetDashboardRowResponse> readRows(
      String orgId, String dashboardId, String versionId, Dashboard dashboard) {
    var rows = dashboardRowDao.getAll(orgId, dashboardId, versionId);
    var seen = new HashSet<String>();
    var orderedRows = new ArrayList<DashboardRow>();
    if (dashboard.getRowOrder() != null) {
      for (var rowId : dashboard.getRowOrder().asList()) {
        rows.stream()
            .filter(row -> row.getRowId().equals(rowId))
            .findFirst()
            .ifPresent(
                row -> {
                  orderedRows.add(row);
                  seen.add(row.getRowId());
                });
      }
    }
    rows.stream().filter(row -> !seen.contains(row.getRowId())).forEach(orderedRows::add);
    return orderedRows.stream()
        .map(
            row ->
                dashboardRowService.read(
                    new DashboardRowRequestContext(orgId, dashboardId, versionId, row.getRowId())))
        .toList();
  }

  protected List<UserEntityRelation> getUserDashboardRelations(String userId, String dashboardId) {
    var faveRelation =
        entityRelationsDao.getRelation(
            userId, new EntityRelationId(DASHBOARD, dashboardId, UserRelationType.DASHBOARD_FAVE));
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

  public GetDashboardResponse update(
      DashboardRequestContext context, UpdateDashboardRequest request) throws Exception {
    validator.validateUpdate(context, request);
    return update(context.orgId(), currentUserProvider.userId(), context.dashboardId(), request);
  }

  private GetDashboardResponse update(
      String orgId, String userId, String dashboardId, UpdateDashboardRequest request)
      throws Exception {
    var dashboardDtoOptional = dashboardDao.get(orgId, dashboardId);
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
    return read(orgId, userId, dashboardId);
  }

  public void handleFav(String userId, String dashboardId, Boolean isFavorite) {
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

  public void delete(DashboardRequestContext context)
      throws BadRequestException, UnAuthorizedException, ResourceNotFoundException {
    validator.validateDelete(context);
    delete(context.orgId(), context.dashboardId());
  }

  private void delete(String orgId, String dashboardId) throws ResourceNotFoundException {
    var dashboardDtoOptional = dashboardDao.get(orgId, dashboardId);
    checkArgument(dashboardDtoOptional.isPresent(), ResourceNotFoundException::new);
    dashboardDao.delete(dashboardId);
  }

  public List<GetDashboardResponse> listDashboards(OrgRequestContext context) throws Exception {
    validator.validateList(context);
    var userId = currentUserProvider.userId();
    var orgId = context.orgId();
    var dashboards = dashboardDao.getAll(orgId);
    var result =
        dashboards.stream()
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

  public ListDashboardVersionsResponse listVersions(DashboardRequestContext context) {
    return dashboardVersionService.list(context.orgId(), context.dashboardId());
  }

  public PublishDashboardVersionResponse publishVersion(DashboardVersionRequestContext context) {
    return dashboardVersionService.publish(
        context.orgId(), context.dashboardId(), context.versionId());
  }
}
