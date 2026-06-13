/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.okapi.logs.core.LogsEventEmitter;
import org.okapi.metrics.ch.ChConstants;
import org.okapi.metrics.ch.ChWriter;

@Slf4j
public class ChLogsWalConsumer {
  private final int batchSize;
  private final ChWriter chWriter;
  private final LogsEventEmitter eventEmitter;
  private final OtelLogsToChRowsConverter converter;
  private final Gson gson = new Gson();

  public ChLogsWalConsumer(
      int batchSize,
      ChWriter chWriter,
      LogsEventEmitter eventEmitter,
      OtelLogsToChRowsConverter converter) {
    this.batchSize = batchSize;
    this.chWriter = chWriter;
    this.eventEmitter = eventEmitter;
    this.converter = converter;
  }

  public void consumeRecords() throws IOException, InterruptedException, ExecutionException {
    var batch = eventEmitter.next(batchSize);
    Multimap<String, String> writeLoad = ArrayListMultimap.create();

    for (var event : batch) {
      var request = ExportLogsServiceRequest.parseFrom(event.payload());
      writeLoad.putAll(
          ChConstants.TBL_LOGS_V1, converter.toRows(request).stream().map(gson::toJson).toList());
    }

    chWriter.writeSyncWithBestEffort(writeLoad);
    if (!batch.isEmpty()) {
      eventEmitter.commit();
    }
  }
}
