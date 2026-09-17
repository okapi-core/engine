/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.web;

import com.google.gson.Gson;
import io.opentelemetry.context.Scope;
import jakarta.servlet.http.HttpServletRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.okapi.demo.rest.CharCount;
import org.okapi.demo.rest.RegexCount;
import org.okapi.demo.rest.WordCount;
import org.okapi.demo.rest.WordCountRequest;
import org.okapi.demo.tracing.TracingHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ServiceProxyController {

  OkHttpClient httpClient = new OkHttpClient.Builder().build();
  Gson gson = new Gson();
  private final TracingHelper tracingHelper;

  public ServiceProxyController(TracingHelper tracingHelper) {
    this.tracingHelper = tracingHelper;
  }

  @Value("${server.port}")
  int port;

  @PostMapping("/word-count")
  public WordCount wordCountProxy(
      @RequestHeader("X-Okapi-Demo-Req-Id") String reqId,
      @RequestBody WordCountRequest text,
      HttpServletRequest request) {
    var serverSpan = tracingHelper.startServerSpan(request, "proxy-word-count");
    try (Scope ignored = serverSpan.makeCurrent()) {
      serverSpan.setAttribute("proxy.req_id", reqId);
      var url = "http://localhost:" + port + "/api/internal/word-count";
      var clientSpan = tracingHelper.startClientSpan("call-internal-word-count", url);
      try (Scope clientScope = clientSpan.makeCurrent()) {
        var httpRequest =
            new Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(gson.toJson(text).getBytes()))
                .addHeader("X-Okapi-Demo-Req-Id", reqId)
                .addHeader("X-Okapi-Demo-Sentence-Id", text.getSentenceId());
        tracingHelper.inject(httpRequest, clientSpan);
        try (var response = httpClient.newCall(httpRequest.build()).execute()) {
          clientSpan.setAttribute("http.status_code", response.code());
          if (!response.isSuccessful()) {
            throw new RuntimeException("Word-count service returned error: " + response.code());
          }
          var body = response.body().string();
          return gson.fromJson(body, WordCount.class);
        }
      } catch (Exception e) {
        tracingHelper.recordError(clientSpan, e);
        throw new RuntimeException("Failed to call word-count service", e);
      } finally {
        clientSpan.end();
      }
    } finally {
      serverSpan.end();
    }
  }

  @PostMapping("/char-count")
  public CharCount charCountProxy(
      @RequestHeader("X-Okapi-Demo-Req-Id") String reqId,
      @RequestBody CharCount text,
      HttpServletRequest request) {
    var serverSpan = tracingHelper.startServerSpan(request, "proxy-char-count");
    try (Scope ignored = serverSpan.makeCurrent()) {
      serverSpan.setAttribute("proxy.req_id", reqId);
      var url = "http://localhost:" + port + "/api/internal/char-count";
      var clientSpan = tracingHelper.startClientSpan("call-internal-char-count", url);
      try (Scope clientScope = clientSpan.makeCurrent()) {
        var httpRequest =
            new Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(gson.toJson(text).getBytes()))
                .addHeader("X-Okapi-Demo-Req-Id", reqId);
        tracingHelper.inject(httpRequest, clientSpan);
        try (var response = httpClient.newCall(httpRequest.build()).execute()) {
          clientSpan.setAttribute("http.status_code", response.code());
          if (!response.isSuccessful()) {
            throw new RuntimeException("char-count service returned error: " + response.code());
          }
          var body = response.body().string();
          return gson.fromJson(body, CharCount.class);
        }
      } catch (Exception e) {
        tracingHelper.recordError(clientSpan, e);
        throw new RuntimeException("Failed to call word-count service", e);
      } finally {
        clientSpan.end();
      }
    } finally {
      serverSpan.end();
    }
  }

  @PostMapping("/regex-count")
  public RegexCount regexCountProxy(
      @RequestHeader("X-Okapi-Demo-Req-Id") String reqId,
      @RequestBody RegexCount text,
      HttpServletRequest request) {
    var serverSpan = tracingHelper.startServerSpan(request, "proxy-regex-count");
    try (Scope ignored = serverSpan.makeCurrent()) {
      serverSpan.setAttribute("proxy.req_id", reqId);
      var url = "http://localhost:" + port + "/api/interval-v2/regex-count";
      var clientSpan = tracingHelper.startClientSpan("call-internal-regex-count", url);
      try (Scope clientScope = clientSpan.makeCurrent()) {
        var httpRequest =
            new Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(gson.toJson(text).getBytes()))
                .addHeader("X-Okapi-Demo-Req-Id", reqId);
        tracingHelper.inject(httpRequest, clientSpan);
        try (var response = httpClient.newCall(httpRequest.build()).execute()) {
          clientSpan.setAttribute("http.status_code", response.code());
          if (!response.isSuccessful()) {
            throw new RuntimeException("regex-count service returned error: " + response.code());
          }
          var body = response.body().string();
          return gson.fromJson(body, RegexCount.class);
        }
      } catch (Exception e) {
        tracingHelper.recordError(clientSpan, e);
        throw new RuntimeException("Failed to call word-count service", e);
      } finally {
        clientSpan.end();
      }
    } finally {
      serverSpan.end();
    }
  }
}
