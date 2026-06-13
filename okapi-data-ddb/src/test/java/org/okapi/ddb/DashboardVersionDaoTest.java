/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.CreateDynamoDBTables;
import org.okapi.data.DaoModule;
import org.okapi.data.dao.DashboardVersionDao;
import org.okapi.data.model.DashboardVersion;
import org.okapi.testutils.OkapiTestUtils;

@Execution(ExecutionMode.CONCURRENT)
public class DashboardVersionDaoTest {

  DashboardVersionDao dao;
  String orgId;
  String dashboardId;
  Injector injector;

  @BeforeEach
  public void setup() {
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    injector = Guice.createInjector(new DaoModule(), new TestDepsModule());
    dao = injector.getInstance(DashboardVersionDao.class);
    orgId = OkapiTestUtils.getTestId(DashboardVersionDaoTest.class);
    dashboardId = OkapiTestUtils.getTestId(DashboardVersionDaoTest.class);
  }

  @Test
  public void fullCycle_createListGet() {
    var version1 = "v1-" + OkapiTestUtils.getTestId(getClass());
    var version2 = "v2-" + OkapiTestUtils.getTestId(getClass());

    var dto1 =
        DashboardVersion.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(version1)
            .status("READY")
            .createdAt(System.currentTimeMillis())
            .createdBy("user-1")
            .note("first")
            .specHash("hash1")
            .build();
    var dto2 =
        DashboardVersion.builder()
            .orgId(orgId)
            .dashboardId(dashboardId)
            .versionId(version2)
            .status("READY")
            .createdAt(System.currentTimeMillis())
            .createdBy("user-2")
            .note("second")
            .specHash("hash2")
            .build();

    dao.save(dto1);
    dao.save(dto2);

    var listed = dao.list(orgId, dashboardId);
    assertEquals(2, listed.size());
    var ids =
        listed.stream()
            .map(DashboardVersion::getVersionId)
            .collect(java.util.stream.Collectors.toSet());
    assertTrue(ids.containsAll(Set.of(version1, version2)));

    var fetched = dao.get(orgId, dashboardId, version1);
    assertTrue(fetched.isPresent());
    assertEquals(version1, fetched.get().getVersionId());
    assertEquals("hash1", fetched.get().getSpecHash());
  }
}
