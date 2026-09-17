/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.executables;

import com.google.gson.Gson;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.okapi.datagen.spans.OtelAstronomyShopSpansGenerator;
import org.okapi.datagen.spans.SpansGeneratorConfig;
import picocli.CommandLine;

@Slf4j
@CommandLine.Command(
    name = "astro-spans-gen",
    mixinStandardHelpOptions = true,
    version = "spans-gen 0.1",
    description =
        "Generates a series of Otel spans which simulate buying journey in astronomy shop app.")
public class AstronomySpansGen implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-f", "--file"},
      description = "Config file. Should map: org.okapi.datagen.spans.SpansGeneratorConfig 1-to-1")
  private File file;

  @CommandLine.Option(
      names = {"-h", "--host"},
      description =
          "Okapi ingester host. Must start with http:// or https:// depending on the deployment.")
  private String host;

  @CommandLine.Option(
      names = {"-p", "--port"},
      description = "Okapi ingester port.")
  private int port;

  public SpansGeneratorConfig getConfig() throws IOException {
    if (file == null) {
      return SpansGeneratorConfig.defaultConfig();
    } else {
      var gson = new Gson();
      var strCfg = Files.readString(file.toPath());
      return gson.fromJson(strCfg, SpansGeneratorConfig.class);
    }
  }

  @Override
  public Integer call() throws Exception {
    var generator = new OtelAstronomyShopSpansGenerator(getConfig());
    var telemetry = generator.generateTelemetry();
    var okHttp = new OkHttpClient();
    submitTraces(okHttp, telemetry.getTraces());
    submitLogs(okHttp, telemetry.getLogs());
    return 0;
  }

  private void submitTraces(OkHttpClient okHttp, List<ExportTraceServiceRequest> requests)
      throws IOException {
    var partialRequest = new Request.Builder().url(host + ":" + port + "/v1/traces");
    for (var req : requests) {
      var request = partialRequest.post(RequestBody.create(req.toByteArray())).build();
      try (var resp = okHttp.newCall(request).execute()) {
        if (resp.isSuccessful()) {
          log.info("Submitted spans successfully, moving on.");
        } else {
          log.error("Could not ingest spans: {} due to : {}", req, resp.body().string());
        }
      }
    }
  }

  private void submitLogs(OkHttpClient okHttp, List<ExportLogsServiceRequest> requests)
      throws IOException {
    var partialRequest = new Request.Builder().url(host + ":" + port + "/v1/logs");
    for (var req : requests) {
      var request = partialRequest.post(RequestBody.create(req.toByteArray())).build();
      try (var resp = okHttp.newCall(request).execute()) {
        if (resp.isSuccessful()) {
          log.info("Submitted logs successfully, moving on.");
        } else {
          log.error("Could not ingest logs: {} due to : {}", req, resp.body().string());
        }
      }
    }
  }
}
