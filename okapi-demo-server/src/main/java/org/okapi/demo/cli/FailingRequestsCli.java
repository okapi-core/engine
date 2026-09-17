/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.demo.cli;

import com.google.gson.Gson;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.okapi.demo.rest.CharCount;
import org.okapi.demo.rest.RegexCount;
import org.okapi.demo.rest.WordCountRequest;
import org.okapi.demo.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class FailingRequestsCli {
  public static void main(String[] args) {
    new SpringApplicationBuilder(FailingRequestsCli.class)
        .web(WebApplicationType.NONE)
        .logStartupInfo(false)
        .run(args);
  }
}

@Component
class FailingRequestRunner {
  private static final Logger log = LoggerFactory.getLogger(FailingRequestRunner.class);
  private static final MediaType JSON = MediaType.parse("application/json");

  private final OkHttpClient httpClient = new OkHttpClient();
  private final Gson gson = new Gson();
  private final TracingHelper tracingHelper;

  @Value("${cli.base-url:http://localhost:8081}")
  private String baseUrl;

  @Value("${cli.nRequests:1}")
  private int configuredNRequests;

  public FailingRequestRunner(TracingHelper tracingHelper) {
    this.tracingHelper = tracingHelper;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void runRequests() {
    int nRequests = configuredNRequests;
    log.info("Sending {} rounds of intentionally failing requests to {}", nRequests, baseUrl);
    for (int i = 0; i < nRequests; i++) {
      sendRegexCount(i);
      sendCharCount(i);
      sendWordCount(i);
    }
    log.info("Finished sending failing requests");
  }

  private void sendRegexCount(int round) {
    String url = baseUrl + "/api/v1/regex-count";
    var payload = new RegexCount("some test sentence", "(unclosed", 0);
    var span = tracingHelper.startClientSpan("cli-call-regex-count", url);
    try (Scope ignored = span.makeCurrent()) {
      RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
      Request.Builder builder = new Request.Builder().url(url).post(body);
      tracingHelper.inject(builder, span);
      executeAndLog(builder.build(), "regex-count", round, span);
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      log.warn("regex-count round {} failed with exception: {}", round, e.toString());
    } finally {
      span.end();
    }
  }

  private void sendCharCount(int round) {
    String url = baseUrl + "/api/v1/char-count";
    var payload = new CharCount(42);
    var span = tracingHelper.startClientSpan("cli-call-char-count", url);
    try (Scope ignored = span.makeCurrent()) {
      RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
      Request.Builder builder = new Request.Builder().url(url).post(body);
      tracingHelper.inject(builder, span);
      executeAndLog(builder.build(), "char-count", round, span);
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      log.warn("char-count round {} failed with exception: {}", round, e.toString());
    } finally {
      span.end();
    }
  }

  private void sendWordCount(int round) {
    String url = baseUrl + "/api/v1/word-count";
    var payload = new WordCountRequest("sentence-" + round, "{\"not\":\"plain text\"}");
    var span = tracingHelper.startClientSpan("cli-call-word-count", url);
    try (Scope ignored = span.makeCurrent()) {
      RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
      Request.Builder builder = new Request.Builder().url(url).post(body);
      tracingHelper.inject(builder, span);
      executeAndLog(builder.build(), "word-count", round, span);
    } catch (Exception e) {
      tracingHelper.recordError(span, e);
      log.warn("word-count round {} failed with exception: {}", round, e.toString());
    } finally {
      span.end();
    }
  }

  private void executeAndLog(Request request, String name, int round, Span span) throws Exception {
    try (Response response = httpClient.newCall(request).execute()) {
      span.setAttribute("http.status_code", response.code());
      if (!response.isSuccessful()) {
        log.warn("{} round {} returned non-2xx: {}", name, round, response.code());
      } else {
        log.info("{} round {} returned success: {}", name, round, response.code());
      }
    }
  }
}
