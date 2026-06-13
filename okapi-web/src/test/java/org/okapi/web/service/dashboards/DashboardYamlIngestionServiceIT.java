/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.dashboards;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.web.auth.TestCommons.addToOrg;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.DashboardVersion;
import org.okapi.web.auth.AbstractIT;
import org.okapi.web.auth.OrgManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlRequest;
import org.okapi.web.dtos.dashboards.yaml.LintDashboardYamlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class DashboardYamlIngestionServiceIT extends AbstractIT {

  @Autowired UserManager userManager;
  @Autowired OrgManager orgManager;
  @Autowired TokenManager tokenManager;
  @Autowired DashboardYamlIngestionService ingestionService;
  @Autowired DashboardVersionDao dashboardVersionDao;
  @Autowired DashboardRowDao dashboardRowDao;
  @Autowired DashboardPanelDao dashboardPanelDao;
  @Autowired DashboardVarDao dashboardVarDao;
  @Autowired DashboardDao dashboardDao;
  @Autowired org.okapi.data.dao.UsersDao usersDao;
  @Autowired org.okapi.data.dao.RelationGraphDao relationGraphDao;

  String adminEmail;
  String adminLogin;
  String adminTempToken;
  String orgId;

  @BeforeEach
  public void setupEach() throws Exception {
    super.setup();
    adminEmail = dedup("admin@yamlingest.com", this.getClass());
    try {
      userManager.signupWithEmailPassword(new CreateUserRequest("Admin", "User", adminEmail, "pw"));
    } catch (Exception ignored) {
    }
    adminLogin = userManager.signInWithEmailPassword(new SignInRequest(adminEmail, "pw"));
    orgId = orgManager.listOrgs(adminLogin).getOrgs().get(0).getOrgId();
    addToOrg(usersDao, relationGraphDao, orgId, adminEmail, true);
    adminTempToken = tokenManager.issueTemporaryToken(adminLogin, orgId);
  }

  @Test
  public void yaml_apply_creates_version_and_writes_snapshot() throws Exception {
    var dashboardId = "dash-" + UUID.randomUUID();
    var yaml =
        """
        version: 1
        dashboard:
          id: "%s"
          title: "Test Dash"
          description: "Test"
          vars:
            - name: metric
              type: METRIC
          rows:
            - id: "row-1"
              title: "Row 1"
              panels:
                - id: "panel-1"
                  title: "Panel 1"
                  queries:
                    - query: "{\\"metric\\":\\"$__{metric}\\",\\"tags\\":{},\\"start\\":0,\\"end\\":0,\\"metricType\\":\\"GAUGE\\"}"
        """
            .formatted(dashboardId);

    var lint =
        ingestionService.lint(
            adminTempToken, LintDashboardYamlRequest.builder().yaml(yaml).build());
    assertTrue(lint.isOk());

    var apply =
        ingestionService.apply(
            adminTempToken, ApplyDashboardYamlRequest.builder().yaml(yaml).note("test").build());
    assertTrue(apply.isOk());
    assertEquals(dashboardId, apply.getDashboardId());
    assertNotNull(apply.getVersionId());

    var versionOpt = dashboardVersionDao.get(orgId, dashboardId, apply.getVersionId());
    assertTrue(versionOpt.isPresent());
    DashboardVersion version = versionOpt.get();
    assertEquals("READY", version.getStatus());

    var rows = dashboardRowDao.getAll(orgId, dashboardId, apply.getVersionId());
    assertEquals(1, rows.size());
    var panels = dashboardPanelDao.getAll(orgId, dashboardId, "row-1", apply.getVersionId());
    assertEquals(1, panels.size());
    var vars = dashboardVarDao.list(orgId, dashboardId, apply.getVersionId());
    assertEquals(1, vars.size());

    var dash = dashboardDao.get(orgId, dashboardId).get();
    assertNull(dash.getActiveVersion(), "activeVersion should not be set on YAML apply");
  }
}
