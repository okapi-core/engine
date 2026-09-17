/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.io.IOException;
import java.util.List;
import org.okapi.exceptions.BadRequestException;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.wal.io.IllegalWalEntryException;

public class ChTracesIngester {
  private final ChWalResources walResources;
  private final boolean directIngestionEnabled;

  public ChTracesIngester(ChWalResources walResources) {
    this(walResources, true);
  }

  public ChTracesIngester(ChWalResources walResources, boolean directIngestionEnabled) {
    this.walResources = walResources;
    this.directIngestionEnabled = directIngestionEnabled;
  }

  public void ingest(ExportTraceServiceRequest request)
      throws BadRequestException, IllegalWalEntryException, IOException {
    checkDirectIngestionEnabled();
    ChTracesValidator.validate(request);
    walResources.appendPayloads(List.of(request.toByteArray()));
  }

  private void checkDirectIngestionEnabled() {
    if (!directIngestionEnabled) {
      throw new BadRequestException(
          "Direct traces ingestion is disabled because this server is running in Kafka consumption mode");
    }
  }
}
