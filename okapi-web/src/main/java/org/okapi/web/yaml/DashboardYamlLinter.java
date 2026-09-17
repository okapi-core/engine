/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.yaml;

import com.google.gson.JsonParser;
import java.util.*;
import java.util.regex.Pattern;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.ResolvedPanelId;
import org.okapi.web.dtos.dashboards.yaml.ResolvedRowId;
import org.okapi.web.dtos.dashboards.yaml.ResolvedYamlIds;
import org.okapi.web.dtos.dashboards.yaml.YamlLintIssue;

public class DashboardYamlLinter {
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$__\\{([^}]+)\\}");

  public LintDashboardYamlResponse lint(DashboardYaml yaml, String dashboardIdOverride) {
    var errors = new ArrayList<YamlLintIssue>();
    var warnings = new ArrayList<YamlLintIssue>();

    if (yaml == null) {
      errors.add(issue("YAML_MISSING", "YAML body is required", "$"));
      return response(false, errors, warnings, null);
    }
    if (yaml.getVersion() != 1) {
      errors.add(issue("UNSUPPORTED_VERSION", "Unsupported yaml version", "$.version"));
    }
    var dashboard = yaml.getDashboard();
    if (dashboard == null) {
      errors.add(issue("DASHBOARD_MISSING", "dashboard is required", "$.dashboard"));
      return response(false, errors, warnings, null);
    }
    if (isBlank(dashboard.getTitle())) {
      errors.add(issue("TITLE_MISSING", "dashboard.title is required", "$.dashboard.title"));
    }
    if (dashboard.getRows() == null || dashboard.getRows().isEmpty()) {
      errors.add(issue("ROWS_MISSING", "At least one row is required", "$.dashboard.rows"));
    }

    var declaredVars = new HashSet<String>();
    if (dashboard.getVars() != null) {
      for (int i = 0; i < dashboard.getVars().size(); i++) {
        var varSpec = dashboard.getVars().get(i);
        if (varSpec == null || isBlank(varSpec.getName())) {
          errors.add(
              issue(
                  "VAR_NAME_MISSING", "Variable name is required", "$.dashboard.vars[" + i + "]"));
          continue;
        }
        if (isBlank(varSpec.getType())
            || !(varSpec.getType().equalsIgnoreCase("METRIC")
                || varSpec.getType().equalsIgnoreCase("TAG_VALUE"))) {
          errors.add(
              issue(
                  "VAR_TYPE_INVALID",
                  "Variable type must be METRIC or TAG_VALUE",
                  "$.dashboard.vars[" + i + "].type"));
        }
        if (!declaredVars.add(varSpec.getName())) {
          errors.add(
              issue(
                  "VAR_DUPLICATE",
                  "Duplicate variable: " + varSpec.getName(),
                  "$.dashboard.vars[" + i + "]"));
        }
        if ("TAG_VALUE".equalsIgnoreCase(varSpec.getType()) && isBlank(varSpec.getTag())) {
          errors.add(
              issue(
                  "VAR_TAG_MISSING", "TAG_VALUE requires tag", "$.dashboard.vars[" + i + "].tag"));
        }
      }
    }

    var resolvedRows = new ArrayList<ResolvedRowId>();
    var rows = dashboard.getRows() == null ? List.<DashboardRowSpec>of() : dashboard.getRows();
    for (int i = 0; i < rows.size(); i++) {
      var row = rows.get(i);
      if (row == null) {
        errors.add(issue("ROW_MISSING", "Row is required", "$.dashboard.rows[" + i + "]"));
        continue;
      }
      var rowId = isBlank(row.getId()) ? ("row-" + (i + 1)) : row.getId();
      var resolvedPanels = new ArrayList<ResolvedPanelId>();
      if (row.getPanels() == null || row.getPanels().isEmpty()) {
        errors.add(
            issue(
                "PANELS_MISSING", "Row must contain panels", "$.dashboard.rows[" + i + "].panels"));
      } else {
        for (int j = 0; j < row.getPanels().size(); j++) {
          var panel = row.getPanels().get(j);
          if (panel == null) {
            errors.add(
                issue(
                    "PANEL_MISSING",
                    "Panel is required",
                    "$.dashboard.rows[" + i + "].panels[" + j + "]"));
            continue;
          }
          var panelId =
              isBlank(panel.getId()) ? ("panel-" + (i + 1) + "-" + (j + 1)) : panel.getId();
          resolvedPanels.add(ResolvedPanelId.builder().id(panelId).build());
          if (panel.getGrammar() == null) {
            errors.add(
                issue(
                    "GRAMMAR_MISSING",
                    "Panel grammar must be specified",
                    "$.dashboard.rows[" + i + "].panels[" + j + "].grammar"));
          }
          if (panel.getQueries() == null || panel.getQueries().isEmpty()) {
            errors.add(
                issue(
                    "QUERIES_MISSING",
                    "Panel must contain queries",
                    "$.dashboard.rows[" + i + "].panels[" + j + "].queries"));
            continue;
          }
          for (int k = 0; k < panel.getQueries().size(); k++) {
            var q = panel.getQueries().get(k);
            var path = "$.dashboard.rows[" + i + "].panels[" + j + "].queries[" + k + "]";
            if (q == null || isBlank(q.getQuery())) {
              errors.add(issue("QUERY_MISSING", "query is required", path + ".query"));
              continue;
            }
            validateVars(q.getQuery(), declaredVars, errors, path);
            validateQueryJson(q.getQuery(), errors, path);
          }
        }
      }
      resolvedRows.add(ResolvedRowId.builder().id(rowId).panels(resolvedPanels).build());
    }

    var dashId = !isBlank(dashboardIdOverride) ? dashboardIdOverride : dashboard.getId();
    if (isBlank(dashId)) {
      dashId = "dashboard";
    }

    var resolved = ResolvedYamlIds.builder().dashboardId(dashId).rows(resolvedRows).build();
    return response(errors.isEmpty(), errors, warnings, resolved);
  }

  private void validateVars(
      String query, Set<String> declared, List<YamlLintIssue> errors, String path) {
    var matcher = VAR_PATTERN.matcher(query);
    while (matcher.find()) {
      var name = matcher.group(1);
      if (!declared.contains(name)) {
        errors.add(issue("VAR_UNDECLARED", "Variable not declared: " + name, path));
      }
    }
  }

  private void validateQueryJson(String query, List<YamlLintIssue> errors, String path) {
    try {
      var json = JsonParser.parseString(query).getAsJsonObject();
      if (json == null || json.isEmpty()) {
        errors.add(issue("QUERY_JSON_INVALID", "Query JSON is invalid", path));
        return;
      }
      if (!hasNonBlankString(json, "metric")
          || !json.has("tags")
          || !json.get("tags").isJsonObject()
          || !hasNonBlankString(json, "metricType")) {
        errors.add(
            issue("QUERY_JSON_INVALID", "Query JSON requires metric, tags, and metricType", path));
        return;
      }
      var metricType = json.get("metricType").getAsString();
      var configField =
          switch (metricType) {
            case "GAUGE" -> "gaugeQueryConfig";
            case "HISTO" -> "histoQueryConfig";
            case "SUM" -> "sumsQueryConfig";
            default -> null;
          };
      var configValue = configField == null ? null : json.getAsJsonObject(configField);
      var configProperty =
          switch (metricType) {
            case "GAUGE" -> "resolution";
            case "HISTO", "SUM" -> "temporality";
            default -> null;
          };
      if (configField == null
          || configValue == null
          || configValue.isEmpty()
          || !hasNonBlankString(configValue, configProperty)
          || ("GAUGE".equals(metricType) && !hasNonBlankString(configValue, "aggregation"))) {
        errors.add(
            issue(
                "QUERY_JSON_INVALID",
                "Query JSON has an unsupported metricType or missing type configuration",
                path));
      }
    } catch (Exception e) {
      errors.add(issue("QUERY_JSON_INVALID", "Query JSON is invalid", path));
    }
  }

  private boolean hasNonBlankString(com.google.gson.JsonObject json, String field) {
    if (field == null) return false;
    return json.has(field)
        && json.get(field).isJsonPrimitive()
        && json.get(field).getAsJsonPrimitive().isString()
        && !isBlank(json.get(field).getAsString());
  }

  private boolean hasNumber(com.google.gson.JsonObject json, String field) {
    return json.has(field)
        && json.get(field).isJsonPrimitive()
        && json.get(field).getAsJsonPrimitive().isNumber();
  }

  private static boolean isBlank(String val) {
    return val == null || val.trim().isEmpty();
  }

  private static YamlLintIssue issue(String code, String message, String path) {
    return YamlLintIssue.builder().code(code).message(message).path(path).build();
  }

  private static LintDashboardYamlResponse response(
      boolean ok,
      List<YamlLintIssue> errors,
      List<YamlLintIssue> warnings,
      ResolvedYamlIds resolved) {
    return LintDashboardYamlResponse.builder()
        .ok(ok)
        .errors(errors)
        .warnings(warnings)
        .resolved(resolved)
        .build();
  }
}
