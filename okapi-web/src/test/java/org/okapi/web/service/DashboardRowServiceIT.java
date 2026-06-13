/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.web.auth.TestCommons.addToOrg;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.model.DashboardPanel;
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
import org.okapi.web.service.dashboards.rows.DashboardRowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Execution(ExecutionMode.CONCURRENT)
public class DashboardRowServiceIT extends AbstractIT {

  @Autowired UserManager userManager;
  @Autowired OrgManager orgManager;
  @Autowired TokenManager tokenManager;
  @Autowired DashboardService dashboardService;
  @Autowired DashboardRowService rowService;
  @Autowired DashboardRowDao rowDao;
  @Autowired DashboardPanelDao panelDao;
  @Autowired RelationGraphDao relationGraphDao;
  @Autowired org.okapi.data.dao.UsersDao usersDao;
  @Autowired DashboardDao dashboardDao;

  String adminEmail;
  String adminLogin;
  String adminTempToken;
  String orgId;

  @BeforeEach
  public void setupEach() throws Exception {
    super.setup();
    adminEmail = dedup("admin@rowservice.com", this.getClass());

    try {
      userManager.signupWithEmailPassword(new CreateUserRequest("Admin", "User", adminEmail, "pw"));
    } catch (Exception ignored) {
    }
    adminLogin = userManager.signInWithEmailPassword(new SignInRequest(adminEmail, "pw"));
    var myOrgs = orgManager.listOrgs(adminLogin);
    orgId = myOrgs.getOrgs().get(0).getOrgId();
    // ensure membership
    addToOrg(usersDao, relationGraphDao, orgId, adminEmail, true);
    adminTempToken = tokenManager.issueTemporaryToken(adminLogin, orgId);
  }

  @Test
  public void fullCycle_row_create_read_update_delete() throws Exception {
    // create a dashboard
    var dashRes =
        dashboardService.create(
            DashboardAccessContext.of(adminTempToken),
            new CreateDashboardRequest("dashNote", "dashTitle", List.of()));
    var dashboardId = dashRes.getDashboardId();
    var versionId = dashRes.getActiveVersion();

    // grant edit to org for this dashboard
    new GrantOrgEditToDashboard(orgId, dashboardId).doTx(relationGraphDao);

    // create row
    var rowId = UUID.randomUUID().toString();
    var createReq =
        CreateDashboardRowRequest.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(versionId)
            .rowId(rowId)
            .title("Row Title")
            .description("Row Desc")
            .build();
    var created = rowService.create(ProtectedResourceContext.of(adminTempToken), createReq);
    assertEquals("Row Title", created.getTitle());
    assertEquals("Row Desc", created.getDescription());
    assertTrue(created.getPanels().isEmpty());

    // insert a panel directly via DAO so read aggregates it
    panelDao.save(
        orgId,
        dashboardId,
        rowId,
        versionId,
        DashboardPanel.builder().panelId("p1").title("Panel 1").note("Note 1").build());

    // read row and verify panel returned
    var read =
        rowService.read(
            new ProtectedResourceContext(
                adminTempToken,
                ResourceIdCreator.createResourceId(orgId, dashboardId, rowId),
                versionId));
    assertEquals(1, read.getPanels().size());
    assertEquals("p1", read.getPanels().get(0).getPanelId());

    // update row title/description
    var updated =
        rowService.update(
            new ProtectedResourceContext(
                adminTempToken,
                ResourceIdCreator.createResourceId(orgId, dashboardId, rowId),
                versionId),
            UpdateDashboardRowRequest.builder()
                .orgId(orgId)
                .panelIds(List.of("p1"))
                .dashboardId(dashboardId)
                .versionId(versionId)
                .title("New Title")
                .description("New Desc")
                .build());
    assertEquals("New Title", updated.getTitle());
    assertEquals("New Desc", updated.getDescription());
    assertEquals(List.of("p1"), updated.getPanelOrder());

    // check that dashboord row order updated
    var dashDtoOpt = dashboardDao.get(orgId, dashboardId);
    assertTrue(dashDtoOpt.isPresent());
    var dashDto = dashDtoOpt.get();
    assertNotNull(dashDto.getRowOrder());
    assertEquals(1, dashDto.getRowOrder().size());
    assertEquals(ResourceOrder.from(List.of(rowId)), dashDto.getRowOrder());

    // delete
    rowService.delete(
        ProtectedResourceContext.of(
            adminTempToken,
            ResourceIdCreator.createResourceId(orgId, dashboardId, rowId),
            versionId));

    var rowOpt = rowDao.get(orgId, dashboardId, versionId, rowId);
    assertTrue(rowOpt.isEmpty());
  }
}
