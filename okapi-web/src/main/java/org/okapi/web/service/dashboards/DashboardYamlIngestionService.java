/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AllArgsConstructor;
import org.okapi.data.dao.*;
import org.okapi.data.exceptions.ResourceNotFoundException;
import org.okapi.data.model.*;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.ids.UuidV7;
import org.okapi.web.auth.AccessManager;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.BulkApplyDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.BulkDashboardYamlLintIssue;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlResponse;
import org.okapi.web.security.CurrentUserProvider;
import org.okapi.web.service.context.OrgRequestContext;
import org.okapi.web.yaml.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@AllArgsConstructor
public class DashboardYamlIngestionService {
  private final DashboardDao dashboardDao;
  private final DashboardRowDao rowDao;
  private final DashboardPanelDao panelDao;
  private final DashboardVarDao varDao;
  private final DashboardVersionDao dashboardVersionDao;
  private final AccessManager accessManager;
  private final CurrentUserProvider currentUserProvider;

  private final DashboardYamlParser parser = new DashboardYamlParser();
  private final DashboardYamlLinter linter = new DashboardYamlLinter();

  public LintDashboardYamlResponse lint(OrgRequestContext context, LintDashboardYamlRequest request)
      throws UnAuthorizedException {
    validateAccess(context.orgId());
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

  public ApplyDashboardYamlResponse apply(
      OrgRequestContext context, ApplyDashboardYamlRequest request)
      throws UnAuthorizedException, ResourceNotFoundException {
    var userId = validateAccess(context.orgId());
    var parsed = parser.parse(request.getYaml());
    var lint = linter.lint(parsed, request.getDashboardId());
    if (!lint.isOk()) {
      return ApplyDashboardYamlResponse.builder().ok(false).status("INVALID").build();
    }
    return importParsed(
        userId,
        context.orgId(),
        parsed,
        lint.getResolved().getDashboardId(),
        request.getNote(),
        request.getYaml());
  }

  @Transactional
  public BulkApplyDashboardYamlResponse applyBulk(OrgRequestContext context, MultipartFile file)
      throws UnAuthorizedException {
    var userId = validateAccess(context.orgId());
    var errors = new ArrayList<BulkDashboardYamlLintIssue>();
    var warnings = new ArrayList<BulkDashboardYamlLintIssue>();
    var documents = new ArrayList<BulkYamlDocument>();

    if (file == null || file.isEmpty()) {
      errors.add(issue("$", "ZIP_EMPTY", "A ZIP file is required"));
    } else {
      try (var input = file.getInputStream()) {
        readZip(input, documents, errors);
      } catch (IOException e) {
        errors.add(issue("$", "ZIP_READ_FAILED", message(e)));
      }
    }

    if (documents.isEmpty() && errors.isEmpty()) {
      errors.add(issue("$", "NO_YAML_FILES", "ZIP must contain at least one YAML file"));
    }
    if (!documents.isEmpty()) {
      for (var document : documents) {
        var lint = document.lint();
        if (!lint.isOk()) {
          addIssues(document.name(), lint.getErrors(), errors);
        }
        addIssues(document.name(), lint.getWarnings(), warnings);
      }
    }
    if (!errors.isEmpty()) {
      return BulkApplyDashboardYamlResponse.builder()
          .ok(false)
          .status("INVALID")
          .imported(List.of())
          .errors(errors)
          .warnings(warnings)
          .build();
    }

    var imported = new ArrayList<ApplyDashboardYamlResponse>();
    for (var document : documents) {
      imported.add(
          importParsed(
              userId,
              context.orgId(),
              document.yaml(),
              document.lint().getResolved().getDashboardId(),
              null,
              document.source()));
    }
    return BulkApplyDashboardYamlResponse.builder()
        .ok(true)
        .status("READY")
        .imported(imported)
        .errors(List.of())
        .warnings(warnings)
        .build();
  }

  private void readZip(
      InputStream input, List<BulkYamlDocument> documents, List<BulkDashboardYamlLintIssue> errors)
      throws IOException {
    try (var zip = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory() || !isYamlFile(entry.getName())) continue;
        var name = entry.getName();
        if (name.startsWith("/") || java.nio.file.Paths.get(name).normalize().startsWith("..")) {
          errors.add(issue(name, "ZIP_ENTRY_INVALID", "ZIP entry path is invalid"));
          continue;
        }
        var source = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        try {
          var yaml = parser.parse(source);
          documents.add(new BulkYamlDocument(name, source, yaml, linter.lint(yaml, null)));
        } catch (IllegalArgumentException e) {
          errors.add(issue(name, "YAML_INVALID", message(e)));
        }
      }
    }
  }

  private ApplyDashboardYamlResponse importParsed(
      String userId,
      String orgId,
      DashboardYaml parsed,
      String dashboardId,
      String note,
      String yaml) {
    var dashboard = ensureDashboard(userId, orgId, dashboardId, parsed);
    var versionId = UuidV7.randomUuid().toString();
    writeSnapshot(orgId, dashboardId, versionId, parsed);
    saveVersionMetadata(userId, orgId, dashboardId, versionId, note, yaml);
    dashboard.setActiveVersion(versionId);
    dashboard.setLastEditor(userId);
    dashboardDao.save(dashboard);
    return ApplyDashboardYamlResponse.builder()
        .ok(true)
        .dashboardId(dashboard.getDashboardId())
        .versionId(versionId)
        .status("READY")
        .build();
  }

  private static void addIssues(
      String file,
      List<org.okapi.web.dtos.dashboards.yaml.YamlLintIssue> source,
      List<BulkDashboardYamlLintIssue> target) {
    if (source == null) return;
    for (var issue : source) {
      target.add(
          BulkDashboardYamlLintIssue.builder()
              .file(file)
              .code(issue.getCode())
              .message(issue.getMessage())
              .path(issue.getPath())
              .build());
    }
  }

  private static BulkDashboardYamlLintIssue issue(String file, String code, String message) {
    return BulkDashboardYamlLintIssue.builder()
        .file(file)
        .code(code)
        .message(message)
        .path("$")
        .build();
  }

  private static boolean isYamlFile(String name) {
    var lower = name.toLowerCase();
    return lower.endsWith(".yaml") || lower.endsWith(".yml");
  }

  private static String message(Exception e) {
    return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
  }

  @AllArgsConstructor
  private static class BulkYamlDocument {
    private final String name;
    private final String source;
    private final DashboardYaml yaml;
    private final LintDashboardYamlResponse lint;

    String name() {
      return name;
    }

    String source() {
      return source;
    }

    DashboardYaml yaml() {
      return yaml;
    }

    LintDashboardYamlResponse lint() {
      return lint;
    }
  }

  private String validateAccess(String orgId) {
    var userId = currentUserProvider.userId();
    accessManager.checkOrgMember(userId, orgId);
    return userId;
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
      String userId, String orgId, String dashboardId, String versionId, String note, String yaml) {
    var version =
        DashboardVersion.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(versionId)
            .status("READY")
            .createdAt(System.currentTimeMillis())
            .createdBy(userId)
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
              .queryConfig(new PanelQueryConfig(panelSpec.getGrammar(), toPanelConfig(panelSpec)))
              .build();
      panelDao.save(orgId, dashboardId, rowId, versionId, panel);
    }
  }

  private List<LabelledQuery> toPanelConfig(DashboardPanelSpec panelSpec) {
    return panelSpec.getQueries().stream()
        .map(
            spec -> LabelledQuery.builder().localId(spec.getLabel()).query(spec.getQuery()).build())
        .toList();
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
