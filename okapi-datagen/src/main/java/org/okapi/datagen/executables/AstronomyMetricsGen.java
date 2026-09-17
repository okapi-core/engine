/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.executables;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.okapi.datagen.spans.MetricsDataGenConfig;
import org.okapi.datagen.spans.MetricsDataGenerator;
import picocli.CommandLine;

@Slf4j
@CommandLine.Command(
    name = "astro-metrics-gen",
    mixinStandardHelpOptions = true,
    version = "metrics-gen 0.1",
    description = "Generates OTEL metrics for the astronomy shop dataset.")
public class AstronomyMetricsGen implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-f", "--file"},
      required = true,
      description = "Config file. Should map: org.okapi.datagen.spans.MetricsDataGenConfig 1-to-1")
  private File file;

  @CommandLine.Option(
      names = {"-h", "--host"},
      required = true,
      description =
          "Okapi ingester host. Must start with http:// or https:// depending on the deployment.")
  private String host;

  @CommandLine.Option(
      names = {"-p", "--port"},
      required = true,
      description = "Okapi ingester port.")
  private int port;

  public MetricsDataGenConfig getConfig() throws IOException {
    if (file == null) {
      return MetricsDataGenConfig.defaultConfig();
    } else {
      var gson = new Gson();
      var strCfg = Files.readString(file.toPath());
      return gson.fromJson(strCfg, MetricsDataGenConfig.class);
    }
  }

  @Override
  public Integer call() throws Exception {
    var generator = new MetricsDataGenerator(getConfig());
    var requests = generator.generate();
    log.info(
        "Generated {} exemplars across {} OTLP metric requests.",
        generator.countExemplars(requests),
        requests.size());
    var okHttp = new OkHttpClient();
    var url = host + ":" + port + "/v1/metrics";
    var partialRequest = new Request.Builder().url(url);

    for (var req : requests) {
      var request = partialRequest.post(RequestBody.create(req.toByteArray())).build();
      try (var resp = okHttp.newCall(request).execute()) {
        if (resp.isSuccessful()) {
          log.info("Submitted metrics successfully, moving on.");
        } else {
          log.error("Could not ingest: {} due to : {}", req, resp.body().string());
        }
      }
    }
    return 0;
  }
}
