/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.data.model.RelationType.ORG_ADMIN;
import static org.okapi.data.model.RelationType.ORG_MEMBER;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.okapi.data.dao.DashboardDao;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.Organization;
import org.okapi.grammar.GRAMMAR;
import org.okapi.web.OkapiWebApp;
import org.okapi.web.clients.OkapiSessionClient;
import org.okapi.web.dtos.dashboards.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    classes = OkapiWebApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardResourceFlowIT {

  private static final OkHttpClient CLIENT = new OkHttpClient();
  private static final MediaType JSON = MediaType.parse("application/json");

  @LocalServerPort int port;

  private final Gson gson = new Gson();

  @Autowired OrgDao orgDao;
  @Autowired DashboardDao dashboardDao;
  @Autowired UsersDao usersDao;
  @Autowired RelationGraphDao relationGraphDao;

  private String orgId;
  private String email;
  private String password;
  private String sessionCookie;
  private OkapiSessionClient sessionClient;

  @BeforeEach
  void setUp() throws Exception {
    orgId = "org-" + UUID.randomUUID();
    email = "dashboard-flow-" + UUID.randomUUID() + "@okapi.test";
    password = "secret123";

    orgDao.save(
        Organization.builder()
            .orgId(orgId)
            .orgName("Dashboard Flow Org")
            .orgCreator("test")
            .created(Instant.now())
            .build());
    var user = usersDao.createIfNotExists("Dashboard", "Tester", email, password, orgId);
    relationGraphDao.addAllRelationships(
        EntityId.of(USER, user.getUserId()),
        EntityId.of(ORG, orgId),
        List.of(ORG_MEMBER, ORG_ADMIN));

    sessionCookie = signIn();
    sessionClient = new OkapiSessionClient(sessionCookie, url(""));
  }

  @Test
  void createsDashboardRowsPanelsAndUpdatesNestedData() throws Exception {
    var dashboard =
        sessionClient.createDashboard(
            orgId,
            CreateDashboardRequest.builder()
                .title("Ops Dashboard")
                .description("Production health")
                .build());

    var dashboardId = dashboard.getDashboardId();
    var versionId = dashboard.getActiveVersion();
    assertFalse(dashboardId.isBlank(), "dashboardId should be present");
    assertFalse(versionId.isBlank(), "activeVersion should be present");
    assertEquals("Ops Dashboard", dashboard.getTitle());
    assertEquals("Production health", dashboard.getDescription());

    assertDashboardRows(dashboardId, versionId);

    var firstRowId = "row-1-traffic";
    var firstRow =
        sessionClient.createDashboardRow(
            orgId,
            dashboardId,
            versionId,
            CreateDashboardRowRequest.builder()
                .rowId(firstRowId)
                .title("Traffic")
                .description("Request and error volume")
                .build());
    assertEquals(firstRowId, firstRow.getRowId());
    assertEquals("Traffic", firstRow.getTitle());
    assertEquals("Request and error volume", firstRow.getDescription());
    assertEquals(0, firstRow.getPanels().size());

    assertDashboardRows(dashboardId, versionId, row(firstRowId, "Traffic", 0));

    var secondRowId = "row-2-latency";
    var secondRow =
        sessionClient.createDashboardRow(
            orgId,
            dashboardId,
            versionId,
            CreateDashboardRowRequest.builder()
                .rowId(secondRowId)
                .title("Latency")
                .description("Tail latency")
                .build());
    assertEquals(secondRowId, secondRow.getRowId());

    assertDashboardRows(
        dashboardId, versionId, row(firstRowId, "Traffic", 0), row(secondRowId, "Latency", 0));

    var requestsPanelReq =
        createPanelRequest(
            "panel-1-requests",
            "Requests",
            "Requests per second",
            "sum(rate(http_requests_total[5m]))");
    var requestsPanel =
        sessionClient.createDashboardPanel(
            orgId, dashboardId, versionId, firstRowId, requestsPanelReq);
    assertEquals(requestsPanelReq.getPanelId(), requestsPanel.getPanelId());
    assertPanel(
        requestsPanel,
        requestsPanelReq.getPanelId(),
        requestsPanelReq.getTitle(),
        requestsPanelReq.getNote(),
        requestsPanelReq.getQueryConfig().getFirst().getQuery(),
        requestsPanelReq.getGrammar());

    assertDashboardRows(
        dashboardId, versionId, row(firstRowId, "Traffic", 1), row(secondRowId, "Latency", 0));

    var errorsPanel =
        sessionClient.createDashboardPanel(
            orgId,
            dashboardId,
            versionId,
            firstRowId,
            createPanelRequest(
                "panel-2-errors",
                "Errors",
                "5xx rate",
                "sum(rate(http_requests_total{status=~\"5..\"}[5m]))"));
    var errorsPanelId = "panel-2-errors";
    assertEquals(errorsPanelId, errorsPanel.getPanelId());

    var p95Panel =
        sessionClient.createDashboardPanel(
            orgId,
            dashboardId,
            versionId,
            secondRowId,
            createPanelRequest(
                "panel-3-p95",
                "p95 latency",
                "95th percentile",
                "histogram_quantile(0.95, rate(duration_bucket[5m]))"));
    var p95PanelId = "panel-3-p95";
    assertEquals(p95PanelId, p95Panel.getPanelId());

    assertDashboardRows(
        dashboardId, versionId, row(firstRowId, "Traffic", 2), row(secondRowId, "Latency", 1));

    var updatedDashboard =
        sessionClient.updateDashboard(
            orgId,
            dashboardId,
            UpdateDashboardRequest.builder()
                .title("Ops Dashboard Updated")
                .desc("Production health and SLOs")
                .rowIds(List.of(secondRowId, firstRowId))
                .isFavorite(true)
                .build());
    assertEquals("Ops Dashboard Updated", updatedDashboard.getTitle());
    assertEquals("Production health and SLOs", updatedDashboard.getDescription());
    assertTrue(updatedDashboard.isFavorite());
    assertEquals(List.of(secondRowId, firstRowId), updatedDashboard.getRowOrder());

    var updatedFirstRow =
        sessionClient.updateDashboardRow(
            orgId,
            dashboardId,
            versionId,
            firstRowId,
            UpdateDashboardRowRequest.builder()
                .title("Traffic Updated")
                .panelIds(List.of(errorsPanelId, requestsPanelReq.getPanelId()))
                .build());
    assertEquals("Traffic Updated", updatedFirstRow.getTitle());
    assertEquals(
        List.of(errorsPanelId, requestsPanelReq.getPanelId()), updatedFirstRow.getPanelOrder());

    var updatedPanel =
        sessionClient.updateDashboardPanel(
            orgId,
            dashboardId,
            versionId,
            firstRowId,
            requestsPanelReq.getPanelId(),
            updatePanelRequest(
                "Requests Updated",
                "Requests per second by job",
                "sum by (job) (rate(http_requests_total[5m]))"));
    assertPanel(
        updatedPanel,
        requestsPanelReq.getPanelId(),
        "Requests Updated",
        "Requests per second by job",
        "sum by (job) (rate(http_requests_total[5m]))",
        GRAMMAR.OKAPI_JSON);

    var finalDashboard = sessionClient.getActiveDashboard(orgId, dashboardId);
    assertEquals(List.of(secondRowId, firstRowId), finalDashboard.getRowOrder());
    assertRow(finalDashboard.getRows().get(0), secondRowId, "Latency", 1);
    assertRow(finalDashboard.getRows().get(1), firstRowId, "Traffic Updated", 2);
    assertPanel(
        findPanel(finalDashboard.getRows().get(1), requestsPanelReq.getPanelId()),
        requestsPanelReq.getPanelId(),
        "Requests Updated",
        "Requests per second by job",
        "sum by (job) (rate(http_requests_total[5m]))",
        GRAMMAR.OKAPI_JSON);
    assertPanel(
        findPanel(finalDashboard.getRows().get(1), errorsPanelId),
        errorsPanelId,
        "Errors",
        "5xx rate",
        "sum(rate(http_requests_total{status=~\"5..\"}[5m]))",
        GRAMMAR.PROMQL);
    assertPanel(
        findPanel(finalDashboard.getRows().get(0), p95PanelId),
        p95PanelId,
        "p95 latency",
        "95th percentile",
        "histogram_quantile(0.95, rate(duration_bucket[5m]))",
        GRAMMAR.PROMQL);
  }

  @Test
  void bulkImportsTwoDashboardsFromZip() throws Exception {
    var zip =
        zip(
            Map.of(
                "first.yaml", dashboardYaml("bulk-first", "Bulk First"),
                "second.yaml", dashboardYaml("bulk-second", "Bulk Second")));

    try (var response = postDashboardZip(zip)) {
      var responseText = response.body().string();
      assertEquals(200, response.code(), responseText);
      var body = JsonParser.parseString(responseText).getAsJsonObject();
      assertTrue(body.get("ok").getAsBoolean());
      assertEquals("READY", body.get("status").getAsString());
      assertEquals(2, body.getAsJsonArray("imported").size());
      assertEquals(0, body.getAsJsonArray("errors").size());
    }
    var firstDashboard = dashboardDao.get(orgId, "bulk-first").orElseThrow();
    var secondDashboard = dashboardDao.get(orgId, "bulk-second").orElseThrow();
    assertNotNull(firstDashboard.getActiveVersion());
    assertNotNull(secondDashboard.getActiveVersion());
    assertEquals("Bulk First", sessionClient.getActiveDashboard(orgId, "bulk-first").getTitle());
    assertEquals("Bulk Second", sessionClient.getActiveDashboard(orgId, "bulk-second").getTitle());
    assertEquals(
        firstDashboard.getActiveVersion(),
        sessionClient
            .getDashboardVersion(orgId, "bulk-first", firstDashboard.getActiveVersion())
            .getActiveVersion());
    assertEquals(
        secondDashboard.getActiveVersion(),
        sessionClient
            .getDashboardVersion(orgId, "bulk-second", secondDashboard.getActiveVersion())
            .getActiveVersion());
  }

  @Test
  void bulkImportDoesNotPersistAnyDashboardWhenOneYamlIsInvalid() throws Exception {
    var zip =
        zip(
            Map.of(
                "valid.yaml",
                dashboardYaml("bulk-valid", "Bulk Valid"),
                "invalid.yaml",
                "version: 1\ndashboard:\n  title: Missing rows\n"));

    try (var response = postDashboardZip(zip)) {
      var responseText = response.body().string();
      assertEquals(200, response.code(), responseText);
      var body = JsonParser.parseString(responseText).getAsJsonObject();
      assertFalse(body.get("ok").getAsBoolean());
      assertEquals("INVALID", body.get("status").getAsString());
      assertEquals(0, body.getAsJsonArray("imported").size());
      assertTrue(body.getAsJsonArray("errors").size() > 0);
      assertTrue(body.getAsJsonArray("errors").toString().contains("invalid.yaml"));
    }

    assertTrue(dashboardDao.get(orgId, "bulk-valid").isEmpty());
  }

  private void assertDashboardRows(String dashboardId, String versionId, RowExpectation... rows)
      throws IOException {
    var active = sessionClient.getActiveDashboard(orgId, dashboardId);
    var version = sessionClient.getDashboardVersion(orgId, dashboardId, versionId);
    assertRows(active, rows);
    assertRows(version, rows);
  }

  private void assertRows(GetDashboardResponse dashboard, RowExpectation... rows) {
    assertEquals(rows.length, dashboard.getRows().size());
    for (var i = 0; i < rows.length; i++) {
      assertRow(dashboard.getRows().get(i), rows[i].rowId(), rows[i].title(), rows[i].panelCount());
    }
  }

  private void assertRow(GetDashboardRowResponse row, String rowId, String title, int panelCount) {
    assertEquals(rowId, row.getRowId());
    assertEquals(title, row.getTitle());
    assertEquals(panelCount, row.getPanels().size());
  }

  private void assertPanel(
      GetDashboardPanelResponse panel,
      String panelId,
      String title,
      String description,
      String query,
      GRAMMAR grammar) {
    assertEquals(panelId, panel.getPanelId());
    assertEquals(title, panel.getTitle());
    assertEquals(description, panel.getDescription());
    assertEquals(query, panel.getQueries().getFirst().getQuery());
    assertEquals(grammar, panel.getGrammar());
  }

  private GetDashboardPanelResponse findPanel(GetDashboardRowResponse row, String panelId) {
    for (var panel : row.getPanels()) {
      if (panelId.equals(panel.getPanelId())) {
        return panel;
      }
    }
    throw new AssertionError("Panel not found: " + panelId);
  }

  private String signIn() throws IOException {
    var request =
        new Request.Builder()
            .url(url("/api/v1/users/sign-in"))
            .post(jsonBody(Map.of("email", email, "password", password)))
            .build();

    try (var response = CLIENT.newCall(request).execute()) {
      assertEquals(204, response.code(), responseBody(response));
      var cookie = response.header("Set-Cookie");
      assertNotNull(cookie);
      assertFalse(cookie.isBlank());
      return cookie.split(";", 2)[0];
    }
  }

  private okhttp3.Response postDashboardZip(byte[] zip) throws IOException {
    var body =
        new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "dashboards.zip",
                RequestBody.create(zip, MediaType.parse("application/zip")))
            .build();
    return CLIENT
        .newCall(
            new Request.Builder()
                .url(url("/api/v1/orgs/" + orgId + "/dashboards/yaml/bulk-apply"))
                .header("Cookie", sessionCookie)
                .post(body)
                .build())
        .execute();
  }

  private byte[] zip(Map<String, String> files) throws IOException {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (var file : files.entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private String dashboardYaml(String id, String title) {
    return """
        version: 1
        dashboard:
          id: %s
          title: %s
          rows:
            - id: overview
              title: Overview
              panels:
                - id: requests
                  title: Requests
                  grammar: OKAPI_JSON
                  queries:
                    - query: >
                        {"metric":"requests","tags":{},"metricType":"SUM","sumsQueryConfig":{"temporality":"DELTA_AGGREGATE"}}
        """
        .formatted(id, title);
  }

  private RequestBody jsonBody(Object body) throws IOException {
    return RequestBody.create(gson.toJson(body), JSON);
  }

  private String responseBody(okhttp3.Response response) throws IOException {
    return response.body().string();
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  private CreateDashboardPanelRequest createPanelRequest(
      String panelId, String title, String note, String query) {
    return CreateDashboardPanelRequest.builder()
        .panelId(panelId)
        .title(title)
        .note(note)
        .grammar(GRAMMAR.PROMQL)
        .queryConfig(List.of(query(query)))
        .build();
  }

  private UpdateDashboardPanelRequest updatePanelRequest(String title, String note, String query) {
    return UpdateDashboardPanelRequest.builder()
        .title(title)
        .note(note)
        .grammar(GRAMMAR.OKAPI_JSON)
        .queryConfig(List.of(query(query)))
        .build();
  }

  private QueryConfig query(String query) {
    return QueryConfig.builder().query(query).build();
  }

  private RowExpectation row(String rowId, String title, int panelCount) {
    return new RowExpectation(rowId, title, panelCount);
  }

  private record RowExpectation(String rowId, String title, int panelCount) {}
}
