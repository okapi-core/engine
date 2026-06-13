/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.okapi.agent.dto.JOB_STATUS;
import org.okapi.agent.dto.QuerySpec;
import org.okapi.data.model.*;
import org.okapi.web.dtos.auth.GetUserProfileResponse;
import org.okapi.web.dtos.dashboards.*;
import org.okapi.web.dtos.dashboards.vars.DASH_VAR_TYPE;
import org.okapi.web.dtos.dashboards.vars.GetVarResponse;
import org.okapi.web.dtos.sources.GetFederatedSourceResponse;
import org.okapi.web.dtos.token.GetTokenResponse;

public class Mappers {

  public static GetDashboardResponse.GetDashboardResponseBuilder mapDashboardToPartial(
      Dashboard dashboardDto,
      PersonalName owner,
      PersonalName lastEditor,
      List<UserEntityRelation> userRelations) {
    var hasFaved =
        userRelations.stream()
            .anyMatch(
                rel -> rel.getEdgeId().getRelationType() == UserRelationType.DASHBOARD_FAVE);
    var lastViewed =
        userRelations.stream()
            .filter(
                rel ->
                    rel.getEdgeId().getRelationType() == UserRelationType.DASHBOARD_LAST_VIEWED)
            .map(s -> Instant.ofEpochMilli(s.getEdgeAttributes().getTimestamp()))
            .findFirst();
    var partial =
        GetDashboardResponse.builder()
            .dashboardId(dashboardDto.getDashboardId())
            .title(dashboardDto.getTitle())
            .description(dashboardDto.getDesc())
            .created(dashboardDto.getCreated())
            .createdBy(owner)
            .lastEditedBy(lastEditor)
            .isFavorite(hasFaved)
            .activeVersion(dashboardDto.getActiveVersion())
            .viewed(lastViewed.orElse(null));
    if (dashboardDto.getRowOrder() != null) {
      partial.rowOrder(dashboardDto.getRowOrder().asList());
    }
    if (dashboardDto.getTags() != null) {
      partial.tags(dashboardDto.getTags().asList());
    }
    return partial;
  }

  public static GetDashboardResponse mapDashboardDtoToResponse(
      Dashboard dashboardDto,
      PersonalName owner,
      PersonalName lastEditor,
      List<UserEntityRelation> entityRelations) {
    return mapDashboardToPartial(dashboardDto, owner, lastEditor, entityRelations).build();
  }

  public static List<String> resourceOrderToResourceIds(ResourceOrder resourceOrder) {
    if (resourceOrder == null || resourceOrder.asList() == null) {
      return Collections.emptyList();
    }
    return resourceOrder.asList();
  }

  public static List<String> getTags(String serialized) {
    if (serialized == null || serialized.isEmpty()) {
      return List.of();
    }
    return Arrays.asList(serialized.split(","));
  }

  public static GetUserProfileResponse mapUserProfileDtoToResponse(User dto) {
    return GetUserProfileResponse.builder()
        .id(dto.getUserId())
        .firstName(dto.getFirstName())
        .lastName(dto.getLastName())
        .email(dto.getEmail())
        .build();
  }

  public static final GetFederatedSourceResponse mapFederatedSourceDtoToResponse(
      FederatedSource dto) {
    return GetFederatedSourceResponse.builder()
        .sourceName(dto.getSourceName())
        .sourceType(dto.getSourceType())
        .createdAt(dto.getCreated())
        .registrationToken(dto.getRegistrationToken())
        .build();
  }

  public static GetDashboardPanelResponse mapDashboardPanelToResponse(DashboardPanel panel) {
    return GetDashboardPanelResponse.builder()
        .panelId(panel.getPanelId())
        .title(panel.getTitle())
        .description(panel.getNote())
        .queryConfig(toWebCfg(panel.getQueryConfig()))
        .build();
  }

  public static MultiQueryPanelConfig mapPanelQueryConfig(List<PanelQueryConfigWDto> cfgs) {
    if (cfgs == null) return new MultiQueryPanelConfig(Collections.emptyList());
    var panelConfigs = new ArrayList<PanelQueryConfig>();
    for (var cfg : cfgs) {
      panelConfigs.add(PanelQueryConfig.builder().query(cfg.getQuery()).build());
    }
    return new MultiQueryPanelConfig(panelConfigs);
  }

  public static MultiQueryPanelWDto toWebCfg(MultiQueryPanelConfig cfg) {
    if (cfg == null) return null;
    return MultiQueryPanelWDto.builder()
        .queries(cfg.getQueryConfigs().stream().map(Mappers::toWebCfg).toList())
        .build();
  }

  public static PanelQueryConfigWDto toWebCfg(PanelQueryConfig cfg) {
    if (cfg == null) return null;
    return PanelQueryConfigWDto.builder().query(cfg.getQuery()).build();
  }

  public static GetDashboardRowResponse toRowResponse(
      DashboardRow row, List<DashboardPanel> panels) {
    var panelResponses = panels.stream().map(Mappers::mapDashboardPanelToResponse).toList();
    return GetDashboardRowResponse.builder()
        .panelOrder(Mappers.resourceOrderToResourceIds(row.getPanelOrder()))
        .rowId(row.getRowId())
        .title(row.getTitle())
        .description(row.getNote())
        .panels(panelResponses)
        .build();
  }

  public static List<org.okapi.agent.dto.PendingJob> mapPendingJobDtosToResponses(
      List<org.okapi.data.model.PendingJob> dtos) {
    return dtos.stream().map(Mappers::mapPendingJobDtoToResponse).toList();
  }

  public static org.okapi.agent.dto.PendingJob mapPendingJobDtoToResponse(
      org.okapi.data.model.PendingJob dto) {
    if (dto == null) return null;
    QuerySpec spec =
        dto.getQuery() != null
            ? QuerySpec.builder().serializedQuery(dto.getQuery().getQuery()).build()
            : null;
    JOB_STATUS status = null;
    if (dto.getJobStatus() != null) {
      status =
          switch (dto.getJobStatus()) {
            case CANCELLED -> JOB_STATUS.CANCELED;
            case PENDING -> JOB_STATUS.PENDING;
            case IN_PROGRESS -> JOB_STATUS.IN_PROGRESS;
            case COMPLETED -> JOB_STATUS.COMPLETED;
            case FAILED -> JOB_STATUS.FAILED;
          };
    }
    return org.okapi.agent.dto.PendingJob.builder()
        .jobId(dto.getJobId())
        .sourceId(dto.getSourceId())
        .spec(spec)
        .jobStatus(status)
        .build();
  }

  public static GetTokenResponse mapTokenMetaToResponse(TokenMetadata tokenMeta) {
    return GetTokenResponse.builder()
        .tokenId(tokenMeta.getTokenId())
        .tokenStatus(tokenMeta.getTokenStatus().name())
        .createdAt(tokenMeta.getCreatedAt())
        .build();
  }

  public static GetVarResponse mapDashboardVarToResponse(DashboardVariable dashVar) {
    if (dashVar == null) return null;
    return new GetVarResponse(
        toWebDashVarType(dashVar.getVarType()), dashVar.getVarName(), dashVar.getTag());
  }

  public static DASH_VAR_TYPE toWebDashVarType(DashboardVariable.Type type) {
    if (type == null) return null;
    return switch (type) {
      case METRIC -> DASH_VAR_TYPE.METRIC;
      case TAG -> DASH_VAR_TYPE.TAG_VALUE;
    };
  }
}
