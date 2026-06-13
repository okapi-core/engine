/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.MultiQueryPanelConfig;
import org.okapi.data.model.PanelQueryConfig;
import org.okapi.data.model.Dashboard;
import org.okapi.data.model.DashboardPanel;
import org.okapi.data.model.DashboardRow;
import org.okapi.data.model.DashboardVariable;
import org.okapi.data.model.DashboardVersion;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.ids.UuidV7;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.auth.tx.GrantOrgEditToDashboard;
import org.okapi.web.auth.tx.GrantOrgReadToDashboardTx;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlResponse;
import org.okapi.web.yaml.DashboardPanelSpec;
import org.okapi.web.yaml.DashboardRowSpec;
import org.okapi.web.yaml.DashboardVarSpec;
import org.okapi.web.yaml.DashboardYaml;
import org.okapi.web.yaml.DashboardYamlLinter;
import org.okapi.web.yaml.DashboardYamlParser;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class DashboardYamlIngestionService {
  private final DashboardDao dashboardDao;
  private final DashboardRowDao rowDao;
  private final DashboardPanelDao panelDao;
  private final DashboardVarDao varDao;
  private final DashboardVersionDao dashboardVersionDao;
  private final TokenManager tokenManager;
  private final AccessManager accessManager;
  private final RelationGraphDao relationGraphDao;

  private final DashboardYamlParser parser = new DashboardYamlParser();
  private final DashboardYamlLinter linter = new DashboardYamlLinter();

  public LintDashboardYamlResponse lint(String tempToken, LintDashboardYamlRequest request)
      throws UnAuthorizedException {
    validateAccess(tempToken);
    try {
      var yaml = parser.parse(request.getYaml());
      return linter.lint(yaml, request.getDashboardId());
    } catch (IllegalArgumentException e) {
      return LintDashboardYamlResponse.builder()
          .ok(false)
          .errors(
              List.of(
                  org.okapi.web.dtos.dashboards.yaml.YamlLintIssue.builder()
                      .code("YAML_INVALID")
                      .message(e.getMessage())
                      .path("$")
                      .build()))
          .warnings(List.of())
          .build();
    }
  }

  public ApplyDashboardYamlResponse apply(String tempToken, ApplyDashboardYamlRequest request)
      throws UnAuthorizedException, ResourceNotFoundException {
    var auth = validateAccess(tempToken);
    var parsed = parser.parse(request.getYaml());
    var lint = linter.lint(parsed, request.getDashboardId());
    if (!lint.isOk()) {
      return ApplyDashboardYamlResponse.builder().ok(false).status("INVALID").build();
    }
    var dashboardId = lint.getResolved().getDashboardId();
    var dashboard = ensureDashboard(auth.userId(), auth.orgId(), dashboardId, parsed);
    var versionId = UuidV7.randomUuid().toString();
    writeSnapshot(auth.orgId(), dashboardId, versionId, parsed);
    saveVersionMetadata(auth, dashboardId, versionId, request.getNote(), request.getYaml());
    return ApplyDashboardYamlResponse.builder()
        .ok(true)
        .dashboardId(dashboard.getDashboardId())
        .versionId(versionId)
        .status("READY")
        .build();
  }

  private AccessManager.AuthContext validateAccess(String tempToken) {
    var userId = tokenManager.getUserId(tempToken);
    var orgId = tokenManager.getOrgId(tempToken);
    accessManager.checkUserIsOrgMember(new AccessManager.AuthContext(userId, orgId));
    return new AccessManager.AuthContext(userId, orgId);
  }

  private Dashboard ensureDashboard(
      String userId, String orgId, String dashboardId, DashboardYaml yaml) {
    var existing = dashboardDao.get(orgId, dashboardId);
    if (existing.isPresent()) {
      return existing.get();
    }
    var dashSpec = yaml.getDashboard();
    var dto =
        Dashboard.builder()
            .dashboardId(dashboardId)
            .orgId(orgId)
            .creator(userId)
            .lastEditor(userId)
            .title(dashSpec == null ? null : dashSpec.getTitle())
            .desc(dashSpec == null ? null : dashSpec.getDescription())
            .build();
    dashboardDao.save(dto);
    new GrantOrgReadToDashboardTx(orgId, dashboardId).doTx(relationGraphDao);
    new GrantOrgEditToDashboard(orgId, dashboardId).doTx(relationGraphDao);
    return dto;
  }

  private void writeSnapshot(
      String orgId, String dashboardId, String versionId, DashboardYaml yaml) {
    var dashSpec = yaml.getDashboard();
    if (dashSpec == null) {
      return;
    }
    if (dashSpec.getVars() != null) {
      for (DashboardVarSpec var : dashSpec.getVars()) {
        if (var == null) continue;
        var dashVar =
            DashboardVariable.builder()
                .varName(var.getName())
                .tag(var.getTag())
                .varType(mapVarType(var.getType()))
                .build();
        varDao.save(orgId, dashboardId, versionId, dashVar);
      }
    }
    if (dashSpec.getRows() == null) {
      return;
    }
    for (int i = 0; i < dashSpec.getRows().size(); i++) {
      var rowSpec = dashSpec.getRows().get(i);
      if (rowSpec == null) continue;
      var rowId = isBlank(rowSpec.getId()) ? ("row-" + (i + 1)) : rowSpec.getId();
      var row =
          DashboardRow.builder()
              .rowId(rowId)
              .title(rowSpec.getTitle())
              .note(rowSpec.getDescription())
              .build();
      rowDao.save(orgId, dashboardId, versionId, row);
      writePanels(orgId, dashboardId, versionId, rowId, rowSpec, i);
    }
  }

  private void saveVersionMetadata(
      AccessManager.AuthContext auth,
      String dashboardId,
      String versionId,
      String note,
      String yaml) {
    var version =
        DashboardVersion.builder()
            .orgId(auth.orgId())
            .dashboardId(dashboardId)
            .versionId(versionId)
            .status("READY")
            .createdAt(System.currentTimeMillis())
            .createdBy(auth.userId())
            .specHash(Integer.toHexString(yaml == null ? 0 : yaml.hashCode()))
            .note(note)
            .build();
    dashboardVersionDao.save(version);
  }

  private void writePanels(
      String orgId,
      String dashboardId,
      String versionId,
      String rowId,
      DashboardRowSpec rowSpec,
      int rowIndex) {
    if (rowSpec.getPanels() == null) {
      return;
    }
    for (int j = 0; j < rowSpec.getPanels().size(); j++) {
      var panelSpec = rowSpec.getPanels().get(j);
      if (panelSpec == null) continue;
      var panelId =
          isBlank(panelSpec.getId())
              ? ("panel-" + (rowIndex + 1) + "-" + (j + 1))
              : panelSpec.getId();
      var panel =
          DashboardPanel.builder()
              .panelId(panelId)
              .title(panelSpec.getTitle())
              .note(panelSpec.getNote())
              .queryConfig(toPanelConfig(panelSpec))
              .build();
      panelDao.save(orgId, dashboardId, rowId, versionId, panel);
    }
  }

  private MultiQueryPanelConfig toPanelConfig(DashboardPanelSpec panelSpec) {
    if (panelSpec == null || panelSpec.getQueries() == null) {
      return new MultiQueryPanelConfig(List.of());
    }
    var configs = new ArrayList<PanelQueryConfig>();
    for (int i = 0; i < panelSpec.getQueries().size(); i++) {
      var q = panelSpec.getQueries().get(i);
      if (q == null || isBlank(q.getQuery())) continue;
      configs.add(PanelQueryConfig.builder().query(q.getQuery()).build());
    }
    return new MultiQueryPanelConfig(configs);
  }

  private static DashboardVariable.Type mapVarType(String type) {
    if (type == null) return null;
    var normalized = type.trim().toUpperCase();
    return switch (normalized) {
      case "METRIC" -> DashboardVariable.Type.METRIC;
      case "TAG_VALUE" -> DashboardVariable.Type.TAG;
      default -> null;
    };
  }

  private static boolean isBlank(String val) {
    return val == null || val.trim().isEmpty();
  }
}
