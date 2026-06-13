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
import org.okapi.data.dao.DashboardRowDao;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.model.DashboardRow;
import org.okapi.testutils.OkapiTestUtils;

@Execution(ExecutionMode.CONCURRENT)
public class DashboardRowDaoTest {

  DashboardRowDao dao;
  String orgId;
  String dashboardId;
  String versionId;
  Injector injector;

  @BeforeEach
  public void setup() {
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    injector = Guice.createInjector(new DaoModule(), new TestDepsModule());
    dao = injector.getInstance(DashboardRowDao.class);
    orgId = OkapiTestUtils.getTestId(DashboardRowDaoTest.class);
    dashboardId = OkapiTestUtils.getTestId(DashboardRowDaoTest.class);
    versionId = OkapiTestUtils.getTestId(DashboardRowDaoTest.class);
  }

  @Test
  public void fullCycle_createListDelete() {
    var row1 =
        DashboardRow.builder()
            .rowId("row1")
            .title("Row 1")
            .note("Note 1")
            .panelOrder(new ResourceOrder(List.of("a", "b")))
            .build();
    var row2 =
        DashboardRow.builder()
            .panelOrder(new ResourceOrder(List.of("a")))
            .rowId("row2")
            .title("Row 2")
            .note("Note 2")
            .build();

    dao.save(orgId, dashboardId, versionId, row1);
    dao.save(orgId, dashboardId, versionId, row2);

    var listed = dao.getAll(orgId, dashboardId, versionId);
    assertEquals(2, listed.size());
    var ids =
        listed.stream().map(DashboardRow::getRowId).collect(java.util.stream.Collectors.toSet());
    assertTrue(ids.containsAll(Set.of("row1", "row2")));

    // validate the panel orders
    var fetchedRow1 = dao.get(orgId, dashboardId, versionId, "row1").get();
    assertEquals(ResourceOrder.from(List.of("a", "b")), fetchedRow1.getPanelOrder());
    var fetchedRow2 = dao.get(orgId, dashboardId, versionId, "row2").get();
    assertEquals(ResourceOrder.from(List.of("a")), fetchedRow2.getPanelOrder());

    // delete
    dao.delete(orgId, dashboardId, versionId, "row1");
    dao.delete(orgId, dashboardId, versionId, "row2");

    // check again
    var listedAfterDelete = dao.getAll(orgId, dashboardId, versionId);
    assertEquals(0, listedAfterDelete.size());
  }
}
