/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.BulkApplyDashboardYamlResponse;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlResponse;
import org.okapi.web.service.context.OrgRequestContext;
import org.okapi.web.service.dashboards.DashboardYamlIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/dashboards/yaml")
public class DashboardYamlController {
  @Autowired DashboardYamlIngestionService ingestionService;

  @PostMapping("/lint")
  public LintDashboardYamlResponse lint(
      @PathVariable String orgId, @RequestBody @Validated LintDashboardYamlRequest req)
      throws Exception {
    return ingestionService.lint(new OrgRequestContext(orgId), req);
  }

  @PostMapping("/apply")
  public ApplyDashboardYamlResponse apply(
      @PathVariable String orgId, @RequestBody @Validated ApplyDashboardYamlRequest req)
      throws Exception {
    return ingestionService.apply(new OrgRequestContext(orgId), req);
  }

  @PostMapping(value = "/bulk-apply", consumes = "multipart/form-data")
  public BulkApplyDashboardYamlResponse bulkApply(
      @PathVariable String orgId, @RequestPart("file") MultipartFile file) {
    return ingestionService.applyBulk(new OrgRequestContext(orgId), file);
  }
}
