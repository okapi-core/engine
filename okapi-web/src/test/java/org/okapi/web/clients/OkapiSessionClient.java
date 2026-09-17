/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.okapi.web.dtos.dashboards.*;

public class OkapiSessionClient {
  private static final OkHttpClient CLIENT = new OkHttpClient();
  private static final MediaType JSON = MediaType.parse("application/json");

  private final String sessionCookie;
  private final String endpoint;
  private final Gson gson;

  public OkapiSessionClient(String sessionCookie, String endpoint) {
    this.sessionCookie = sessionCookie;
    this.endpoint = endpoint;
    this.gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Instant.class,
                (JsonDeserializer<Instant>)
                    (json, type, context) -> Instant.parse(json.getAsString()))
            .create();
  }

  public GetDashboardResponse createDashboard(String orgId, CreateDashboardRequest request)
      throws IOException {
    return post("/api/v1/orgs/" + orgId + "/dashboards", request, GetDashboardResponse.class);
  }

  public GetDashboardResponse getActiveDashboard(String orgId, String dashboardId)
      throws IOException {
    return get(activeDashboardPath(orgId, dashboardId), GetDashboardResponse.class);
  }

  public GetDashboardResponse getDashboardVersion(
      String orgId, String dashboardId, String versionId) throws IOException {
    return get(dashboardVersionPath(orgId, dashboardId, versionId), GetDashboardResponse.class);
  }

  public GetDashboardResponse updateDashboard(
      String orgId, String dashboardId, UpdateDashboardRequest request) throws IOException {
    return post(
        "/api/v1/orgs/" + orgId + "/dashboards/" + dashboardId + "/update",
        request,
        GetDashboardResponse.class);
  }

  public GetDashboardRowResponse createDashboardRow(
      String orgId, String dashboardId, String versionId, CreateDashboardRowRequest request)
      throws IOException {
    return post(
        dashboardVersionPath(orgId, dashboardId, versionId) + "/rows",
        request,
        GetDashboardRowResponse.class);
  }

  public GetDashboardRowResponse updateDashboardRow(
      String orgId,
      String dashboardId,
      String versionId,
      String rowId,
      UpdateDashboardRowRequest request)
      throws IOException {
    return post(
        rowPath(orgId, dashboardId, versionId, rowId) + "/update",
        request,
        GetDashboardRowResponse.class);
  }

  public GetDashboardPanelResponse createDashboardPanel(
      String orgId,
      String dashboardId,
      String versionId,
      String rowId,
      CreateDashboardPanelRequest request)
      throws IOException {
    return post(
        rowPath(orgId, dashboardId, versionId, rowId) + "/panels",
        request,
        GetDashboardPanelResponse.class);
  }

  public GetDashboardPanelResponse updateDashboardPanel(
      String orgId,
      String dashboardId,
      String versionId,
      String rowId,
      String panelId,
      UpdateDashboardPanelRequest request)
      throws IOException {
    return post(
        rowPath(orgId, dashboardId, versionId, rowId) + "/panels/" + panelId + "/update",
        request,
        GetDashboardPanelResponse.class);
  }

  private <Req, Res> Res post(String path, Req body, Class<Res> responseType) throws IOException {
    var request =
        new Request.Builder()
            .url(url(path))
            .header("Cookie", sessionCookie)
            .post(RequestBody.create(gson.toJson(body), JSON))
            .build();
    return execute(request, responseType);
  }

  private <Res> Res get(String path, Class<Res> clazz) throws IOException {
    var request =
        new Request.Builder().url(url(path)).header("Cookie", sessionCookie).get().build();
    return execute(request, clazz);
  }

  private <Res> Res execute(Request request, Class<Res> responseType) throws IOException {
    try (var response = CLIENT.newCall(request).execute()) {
      var body = response.body().string();
      assertEquals(200, response.code(), body);
      return gson.fromJson(body, responseType);
    }
  }

  private String activeDashboardPath(String orgId, String dashboardId) {
    return "/api/v1/orgs/" + orgId + "/dashboards/" + dashboardId + "/versions/active";
  }

  private String dashboardVersionPath(String orgId, String dashboardId, String versionId) {
    return "/api/v1/orgs/" + orgId + "/dashboards/" + dashboardId + "/versions/" + versionId;
  }

  private String rowPath(String orgId, String dashboardId, String versionId, String rowId) {
    return dashboardVersionPath(orgId, dashboardId, versionId) + "/rows/" + rowId;
  }

  private String url(String path) {
    return endpoint + path;
  }
}
