/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ddb;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.okapi.data.CreateDynamoDBTables;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.model.ResourceOrder;
import org.okapi.data.model.Tags;
import org.okapi.data.ddb.dao.DashboardDaoImpl;
import org.okapi.data.model.Dashboard;
import org.okapi.testutils.OkapiTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

@Execution(ExecutionMode.CONCURRENT)
public class DashboardDaoImplTest {

  DashboardDao dao;

  @BeforeEach
  public void setup() {
    CreateDynamoDBTables.createTables(OkapiTestUtils.getLocalStackDynamoDbClient());
    var enhanced =
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(OkapiTestUtils.getLocalStackDynamoDbClient())
            .build();
    dao = new DashboardDaoImpl(enhanced);
  }

  @Test
  public void testFullLifecycle() throws Exception {
    var orgId = OkapiTestUtils.getTestId(getClass());
    var dashId = OkapiTestUtils.getTestId(getClass());

    var dto =
        Dashboard.builder()
            .orgId(orgId)
            .dashboardId(dashId)
            .title("Title")
            .desc("Desc")
            .tags(Tags.of("a", "b"))
            .rowOrder(new ResourceOrder(List.of("a", "b", "c")))
            .build();
    dao.save(dto);

    var listed = dao.getAll(orgId);
    assertEquals(1, listed.size());
    assertEquals(dashId, listed.get(0).getDashboardId());
    assertEquals(new ResourceOrder(List.of("a", "b", "c")), listed.get(0).getRowOrder());

    dao.delete(dashId);

    var afterDelete = dao.getAll(orgId);
    assertEquals(0, afterDelete.size());
  }
}
