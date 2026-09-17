/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.okapi.data.model.*;
import org.okapi.grammar.GRAMMAR;
import org.okapi.web.dtos.auth.GetUserProfileResponse;
import org.okapi.web.dtos.dashboards.*;
import org.okapi.web.dtos.dashboards.vars.DASH_VAR_TYPE;
import org.okapi.web.dtos.dashboards.vars.GetVarResponse;
import org.okapi.web.dtos.org.GetOrgSummaryResponse;

public class Mappers {

  public static GetDashboardResponse.GetDashboardResponseBuilder mapDashboardToPartial(
      Dashboard dashboardDto,
      PersonalName owner,
      PersonalName lastEditor,
      List<UserEntityRelation> userRelations) {
    var hasFaved =
        userRelations.stream()
            .anyMatch(rel -> rel.getEdgeId().getRelationType() == UserRelationType.DASHBOARD_FAVE);
    var lastViewed =
        userRelations.stream()
            .filter(
                rel -> rel.getEdgeId().getRelationType() == UserRelationType.DASHBOARD_LAST_VIEWED)
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

  public static GetUserProfileResponse mapUserProfileDtoToResponse(
      User dto, OrgSummary orgSummary) {
    var builder =
        GetUserProfileResponse.builder()
            .id(dto.getUserId())
            .firstName(dto.getFirstName())
            .lastName(dto.getLastName())
            .email(dto.getEmail());
    if (orgSummary != null) {
      builder.orgSummary(
          GetOrgSummaryResponse.builder()
              .orgId(orgSummary.getOrgId())
              .orgName(orgSummary.getOrgName())
              .totalMembers(orgSummary.getTotalMembers())
              .build());
    }
    return builder.build();
  }

  public static GetDashboardPanelResponse mapDashboardPanelToResponse(DashboardPanel panel) {
    var queryConfig = panel.getQueryConfig();
    return GetDashboardPanelResponse.builder()
        .panelId(panel.getPanelId())
        .title(panel.getTitle())
        .description(panel.getNote())
        .queries(toWebCfg(queryConfig))
        .grammar(queryConfig == null ? null : queryConfig.getGrammar())
        .build();
  }

  public static PanelQueryConfig mapPanelQueryConfig(GRAMMAR grammar, List<QueryConfig> cfgs) {
    var panelConfigs = new ArrayList<LabelledQuery>();
    if (cfgs != null) {
      for (var cfg : cfgs) {
        panelConfigs.add(LabelledQuery.builder().query(cfg.getQuery()).build());
      }
    }
    return new PanelQueryConfig(grammar, panelConfigs);
  }

  public static List<QueryConfig> toWebCfg(PanelQueryConfig cfg) {
    if (cfg == null || cfg.getQueryConfigs() == null) return null;
    return cfg.getQueryConfigs().stream().map(Mappers::toWebCfg).toList();
  }

  public static QueryConfig toWebCfg(LabelledQuery cfg) {
    if (cfg == null) return null;
    return QueryConfig.builder().query(cfg.getQuery()).build();
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
