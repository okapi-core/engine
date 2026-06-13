/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.web.auth.TestCommons.addToOrg;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.ResourceOrder;
import org.okapi.web.auth.AbstractIT;
import org.okapi.web.auth.OrgManager;
import org.okapi.web.auth.TokenManager;
import org.okapi.web.auth.UserManager;
import org.okapi.web.auth.tx.GrantOrgEditToDashboard;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.SignInRequest;
import org.okapi.web.dtos.dashboards.*;
import org.okapi.web.service.context.DashboardAccessContext;
import org.okapi.web.service.dashboards.DashboardService;
import org.okapi.web.service.dashboards.panels.DashboardPanelService;
import org.okapi.web.service.dashboards.rows.DashboardRowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Execution(ExecutionMode.CONCURRENT)
public class DashboardPanelServiceIT extends AbstractIT {

  @Autowired UserManager userManager;
  @Autowired OrgManager orgManager;
  @Autowired TokenManager tokenManager;
  @Autowired DashboardService dashboardService;
  @Autowired DashboardRowService rowService;
  @Autowired DashboardPanelService panelService;
  @Autowired DashboardRowDao rowDao;
  @Autowired DashboardPanelDao panelDao;
  @Autowired RelationGraphDao relationGraphDao;
  @Autowired org.okapi.data.dao.UsersDao usersDao;

  String adminEmail;
  String adminLogin;
  String adminTempToken;
  String orgId;

  @BeforeEach
  public void setupEach() throws Exception {
    super.setup();
    adminEmail = dedup("admin@panelservice.com", this.getClass());

    try {
      userManager.signupWithEmailPassword(new CreateUserRequest("Admin", "User", adminEmail, "pw"));
    } catch (Exception ignored) {
    }
    adminLogin = userManager.signInWithEmailPassword(new SignInRequest(adminEmail, "pw"));
    var myOrgs = orgManager.listOrgs(adminLogin);
    orgId = myOrgs.getOrgs().get(0).getOrgId();
    addToOrg(usersDao, relationGraphDao, orgId, adminEmail, true);
    adminTempToken = tokenManager.issueTemporaryToken(adminLogin, orgId);
  }

  @Test
  public void fullCycle_panel_create_read_update_delete() throws Exception {
    // create dashboard
    var dashRes =
        dashboardService.create(
            DashboardAccessContext.of(adminTempToken),
            new CreateDashboardRequest("dashNote", "dashTitle", List.of()));
    var dashboardId = dashRes.getDashboardId();
    var versionId = dashRes.getActiveVersion();
    new GrantOrgEditToDashboard(orgId, dashboardId).doTx(relationGraphDao);

    // create row to attach panel
    var rowId = "row__" + UUID.randomUUID().toString();
    rowService.create(
        ProtectedResourceContext.of(adminTempToken),
        CreateDashboardRowRequest.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(versionId)
            .rowId(rowId)
            .title("Row Title")
            .description("Row Desc")
            .build());

    // create panel
    var createPanelRes =
        panelService.create(
            ProtectedResourceContext.of(adminTempToken),
            CreateDashboardPanelRequest.builder()
                .orgId(orgId)
                .dashboardId(dashboardId)
                .versionId(versionId)
                .rowId(rowId)
                .panelId("p1")
                .title("Panel 1")
                .note("Note 1")
                .queryConfig(
                    Arrays.asList(
                        PanelQueryConfigWDto.builder()
                            .query("rate(http_requests_total[1m])")
                            .build()))
                .build());
    assertEquals("p1", createPanelRes.getPanelId());

    // read panel
    var readRes =
        panelService.read(
            new ProtectedResourceContext(
                adminTempToken,
                ResourceIdCreator.createResourceId(orgId, dashboardId, rowId, "p1"),
                versionId));
    assertEquals("Panel 1", readRes.getTitle());
    assertNotNull(readRes.getQueryConfig());

    // update panel
    var updated =
        panelService.update(
            ProtectedResourceContext.of(
                adminTempToken,
                ResourceIdCreator.createResourceId(orgId, dashboardId, rowId, "p1"),
                versionId),
            UpdateDashboardPanelRequest.builder()
                .orgId(orgId)
                .dashboardId(dashboardId)
                .versionId(versionId)
                .rowId(rowId)
                .title("New Title")
                .note("New Note")
                .queryConfig(
                    Arrays.asList(PanelQueryConfigWDto.builder().query("sum(rate(x[5m]))").build()))
                .build());
    assertEquals("New Title", updated.getTitle());

    // check that dashboard row has panel in its panel order
    var dashRowOpt = rowDao.get(orgId, dashboardId, versionId, rowId);
    assertTrue(dashRowOpt.isPresent());
    var dashRow = dashRowOpt.get();
    assertNotNull(dashRow.getPanelOrder());
    assertEquals(1, dashRow.getPanelOrder().asList().size());
    assertEquals(ResourceOrder.from("p1"), dashRow.getPanelOrder());

    // delete panel
    panelService.delete(
        ProtectedResourceContext.of(
            adminTempToken,
            ResourceIdCreator.createResourceId(orgId, dashboardId, rowId, "p1"),
            versionId));
    assertTrue(panelDao.get(orgId, dashboardId, rowId, versionId, "p1").isEmpty());
  }
}
