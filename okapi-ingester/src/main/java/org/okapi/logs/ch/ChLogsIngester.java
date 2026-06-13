/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import java.io.IOException;
import java.util.List;
import org.okapi.metrics.ch.ChWalResources;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;

public class ChLogsIngester {
  private final ChWalResources walResources;

  public ChLogsIngester(ChWalResources walResources) {
    this.walResources = walResources;
  }

  public ExportLogsServiceResponse ingest(ExportLogsServiceRequest serviceRequest)
      throws IOException, IllegalWalEntryException {
    walResources.getWriter().appendBatch(List.of(toWalEntry(serviceRequest)));
    return ExportLogsServiceResponse.newBuilder().build();
  }

  private WalEntry toWalEntry(ExportLogsServiceRequest request) throws IOException {
    var lsnSupplier = walResources.getSupplier();
    return new WalEntry(lsnSupplier.next(), request.toByteArray());
  }
}
