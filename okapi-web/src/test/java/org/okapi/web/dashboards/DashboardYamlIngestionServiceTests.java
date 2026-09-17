/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dashboards;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.UsersDao;
import org.okapi.grammar.GRAMMAR;
import org.okapi.web.auth.OrgIdSupplier;
import org.okapi.web.auth.UserManager;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.dashboards.GetDashboardResponse;
import org.okapi.web.dtos.dashboards.GetDashboardRowResponse;
import org.okapi.web.dtos.dashboards.yaml.ApplyDashboardYamlRequest;
import org.okapi.web.security.OkapiUserPrincipal;
import org.okapi.web.service.context.DashboardVersionRequestContext;
import org.okapi.web.service.context.OrgRequestContext;
import org.okapi.web.service.dashboards.DashboardService;
import org.okapi.web.service.dashboards.DashboardYamlIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
public class DashboardYamlIngestionServiceTests {
  public static AtomicBoolean allSetup = new AtomicBoolean(false);
  @Autowired DashboardYamlIngestionService service;
  @Autowired DashboardService dashboardService;
  @Autowired UserManager userManager;
  @Autowired OrgIdSupplier orgIdSupplier;
  @Autowired UsersDao usersDao;

  private static final String TEST_EMAIL = "dashboard-yaml-" + UUID.randomUUID() + "@okapi.test";
  private static final String TEST_PASSWORD = "password123";

  @BeforeEach
  public void setup() {
    if (allSetup.compareAndSet(false, true)) {
      userManager.signupWithEmailPassword(
          new CreateUserRequest("Dashboard", "YamlTest", TEST_EMAIL, TEST_PASSWORD));
    }

    var user = usersDao.getWithEmail(TEST_EMAIL).orElseThrow();
    var principal = OkapiUserPrincipal.from(user);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
  }

  @Test
  public void testSanity() throws Exception {
    var yaml =
        Resources.toString(
            Resources.getResource("dashboard-yamls/okapi-monitoring.v2.yaml"),
            StandardCharsets.UTF_8);
    var id = UUID.randomUUID().toString();
    var applied =
        service.apply(
            new OrgRequestContext(orgIdSupplier.getOrgId()),
            new ApplyDashboardYamlRequest(id, yaml, "note"));

    assertTrue(applied.isOk());
    assertEquals(id, applied.getDashboardId());
    assertEquals("READY", applied.getStatus());
    assertNotNull(applied.getVersionId());

    var persisted =
        dashboardService.readVersion(
            new DashboardVersionRequestContext(
                orgIdSupplier.getOrgId(), applied.getDashboardId(), applied.getVersionId()));

    assertEquals(id, persisted.getDashboardId());
    assertEquals("Okapi Monitoring", persisted.getTitle());
    assertEquals(
        "Okapi API traffic, latency, errors, and concurrency.", persisted.getDescription());
    assertEquals(6, persisted.getRows().size());

    assertRow(
        persisted,
        "api-traffic",
        "API Traffic",
        new String[][] {
          {"request-count", "Requests", "okapi.http.server.requests"},
          {"server-errors", "Server Errors", "okapi.http.server.requests"}
        });
    assertRow(
        persisted,
        "api-latency",
        "API Latency",
        new String[][] {{"request-duration", "Request Duration", "okapi.http.server.duration"}});
    assertRow(
        persisted,
        "api-concurrency",
        "API Concurrency",
        new String[][] {
          {"requests-in-flight", "Requests In Flight", "okapi.http.server.in_flight"}
        });
    assertRow(
        persisted,
        "ingestion-flow",
        "Ingestion and WAL",
        new String[][] {
          {"wal-events", "WAL Events", "okapi.wal.appended"},
          {"wal-bytes", "WAL Bytes", "okapi.wal.bytes"},
          {"consumer-events", "Consumer Events", "okapi.consumer.events"}
        });
    assertRow(
        persisted,
        "storage",
        "ClickHouse Storage",
        new String[][] {
          {"rows-written", "Rows Written", "okapi.storage.write.rows"},
          {"write-duration", "Write Duration", "okapi.storage.write.duration"}
        });
    assertRow(
        persisted,
        "runtime",
        "Ingester Runtime",
        new String[][] {
          {"cpu-usage", "CPU Usage", "process.cpu.usage"},
          {"heap-used", "Heap Used", "jvm.memory.used"},
          {"gc-pause", "GC Pause", "jvm.gc.pause"}
        });
  }

  private void assertRow(
      GetDashboardResponse dashboard, String rowId, String title, String[][] expectedPanels) {
    var row =
        dashboard.getRows().stream()
            .filter(candidate -> rowId.equals(candidate.getRowId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Row not found: " + rowId));
    assertEquals(title, row.getTitle());
    assertEquals(expectedPanels.length, row.getPanels().size());
    for (var expected : expectedPanels) {
      assertPanel(row, expected[0], expected[1], expected[2]);
    }
  }

  private void assertPanel(
      GetDashboardRowResponse row, String panelId, String title, String expectedMetric) {
    var panel =
        row.getPanels().stream()
            .filter(candidate -> panelId.equals(candidate.getPanelId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Panel not found: " + panelId));
    assertEquals(title, panel.getTitle());
    assertEquals(GRAMMAR.OKAPI_JSON, panel.getGrammar());
    assertEquals(1, panel.getQueries().size());
    assertTrue(panel.getQueries().getFirst().getQuery().contains(expectedMetric));
  }
}
