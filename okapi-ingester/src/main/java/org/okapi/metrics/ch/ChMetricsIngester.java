/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

import static org.okapi.metrics.ch.MetricsValidator.validate;

import com.google.gson.Gson;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.otel.ConversionConfig;
import org.okapi.metrics.otel.DotToUnderscorePipeline;
import org.okapi.metrics.otel.IdentityRewritePipeline;
import org.okapi.metrics.otel.MetricsPostProcessor;
import org.okapi.metrics.otel.OtelConverter;
import org.okapi.metrics.otel.RewritePostProcessor;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.wal.io.IllegalWalEntryException;

@Slf4j
public class ChMetricsIngester {
  private final OtelConverter otelConverter;
  private final ChWalResources walResources;
  private final boolean directIngestionEnabled;
  private final Gson gson = new Gson();

  public ChMetricsIngester(OtelConverter otelConverter, ChWalResources walResources) {
    this(otelConverter, walResources, true);
  }

  public ChMetricsIngester(
      OtelConverter otelConverter, ChWalResources walResources, boolean directIngestionEnabled) {
    this.otelConverter = otelConverter;
    this.walResources = walResources;
    this.directIngestionEnabled = directIngestionEnabled;
  }

  protected byte[] toWalPayload(ExportMetricsRequest request) {
    var payload = gson.toJson(request);
    return payload.getBytes(StandardCharsets.UTF_8);
  }

  public void ingestOtelProtobuf(ExportMetricsServiceRequest exportMetricsServiceRequest)
      throws BadRequestException, IllegalWalEntryException, IOException {
    checkDirectIngestionEnabled();
    List<ExportMetricsRequest> converted =
        otelConverter.toOkapiRequests(exportMetricsServiceRequest);
    converted = buildPostProcessor(null).process(converted);
    validate(converted);
    var walPayloads = converted.stream().map(this::toWalPayload).toList();
    walResources.appendPayloads(walPayloads);
  }

  public void ingestOtelProtobuf(
      ExportMetricsServiceRequest exportMetricsServiceRequest, ConversionConfig conversionConfig)
      throws BadRequestException, IllegalWalEntryException, IOException {
    checkDirectIngestionEnabled();
    List<ExportMetricsRequest> converted =
        otelConverter.toOkapiRequests(exportMetricsServiceRequest);
    converted = buildPostProcessor(conversionConfig).process(converted);
    validate(converted);
    var walPayloads = converted.stream().map(this::toWalPayload).toList();
    walResources.appendPayloads(walPayloads);
  }

  private MetricsPostProcessor buildPostProcessor(ConversionConfig conversionConfig) {
    if (conversionConfig != null && conversionConfig.isPrometheusDialect()) {
      return new RewritePostProcessor(new DotToUnderscorePipeline());
    }
    return new RewritePostProcessor(new IdentityRewritePipeline());
  }

  private void checkDirectIngestionEnabled() {
    if (!directIngestionEnabled) {
      throw new BadRequestException(
          "Direct metrics ingestion is disabled because this server is running in Kafka consumption mode");
    }
  }
}
