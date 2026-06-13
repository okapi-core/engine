/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.CreateDynamoDBTables;
import org.okapi.data.DaoModule;
import org.okapi.data.dao.DashboardPanelDao;
import org.okapi.data.model.ExpectedResultType;
import org.okapi.data.model.MultiQueryPanelConfig;
import org.okapi.data.model.PanelQueryConfig;
import org.okapi.data.ddb.dao.DashboardPanelDaoDdbImpl;
import org.okapi.data.model.DashboardPanel;
import org.okapi.testutils.OkapiTestUtils;

@Execution(ExecutionMode.CONCURRENT)
public class DashboardPanelDaoTest {

  DashboardPanelDao dao;
  String orgId;
  String dashboardId;
  String rowId;
  String versionId;
  Injector injector;

  @BeforeEach
  public void setup() {
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    injector = Guice.createInjector(new DaoModule(), new TestDepsModule());
    dao = injector.getInstance(DashboardPanelDaoDdbImpl.class);
    orgId = OkapiTestUtils.getTestId(DashboardPanelDaoTest.class);
    dashboardId = OkapiTestUtils.getTestId(DashboardPanelDaoTest.class);
    rowId = OkapiTestUtils.getTestId(DashboardPanelDaoTest.class);
    versionId = OkapiTestUtils.getTestId(DashboardPanelDaoTest.class);
  }

  @Test
  public void fullCycle_createListDelete() {
    var panel1 =
        DashboardPanel.builder()
            .panelId("panel1")
            .title("Panel 1")
            .note("Note 1")
            .queryConfig(createSamplePanelQueryConfig())
            .build();
    var panel2 =
        DashboardPanel.builder()
            .queryConfig(createSamplePanelQueryConfig())
            .panelId("panel2")
            .title("Panel 2")
            .note("Note 2")
            .build();

    dao.save(orgId, dashboardId, rowId, versionId, panel1);
    dao.save(orgId, dashboardId, rowId, versionId, panel2);

    // fetched
    var fetched1 = dao.get(orgId, dashboardId, rowId, versionId, "panel1").get();
    assertEquals("Panel 1", fetched1.getTitle());
    var fetched2 = dao.get(orgId, dashboardId, rowId, versionId, "panel2").get();
    assertEquals("Panel 2", fetched2.getTitle());

    // query configs
    assertEquals(1, fetched1.getQueryConfig().getQueryConfigs().size());
    assertEquals(1, fetched2.getQueryConfig().getQueryConfigs().size());

    // validate equality
    var expectedConfig = createSamplePanelQueryConfig().getQueryConfigs().get(0);
    assertEquals(panel1.getQueryConfig().getQueryConfigs().get(0), expectedConfig);
    assertEquals(panel2.getQueryConfig().getQueryConfigs().get(0), expectedConfig);

    var listed = dao.getAll(orgId, dashboardId, rowId, versionId);
    assertEquals(2, listed.size());
    var ids =
        listed.stream()
            .map(DashboardPanel::getPanelId)
            .collect(java.util.stream.Collectors.toSet());
    assertTrue(ids.containsAll(Set.of("panel1", "panel2")));

    dao.delete(orgId, dashboardId, rowId, versionId, "panel1");
    dao.delete(orgId, dashboardId, rowId, versionId, "panel2");

    var listedAfterDelete = dao.getAll(orgId, dashboardId, rowId, versionId);
    assertEquals(0, listedAfterDelete.size());
  }

  public MultiQueryPanelConfig createSamplePanelQueryConfig() {
    return new MultiQueryPanelConfig(
        List.of(
            PanelQueryConfig.builder()
                .query("rate(http_requests_total[5m])")
                .expectedResultType(ExpectedResultType.TIME_MATRIX)
                .build()));
  }
}
