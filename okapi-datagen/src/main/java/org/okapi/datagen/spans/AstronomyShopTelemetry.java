/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import java.util.List;
import lombok.Value;

@Value
public class AstronomyShopTelemetry {
  List<ExportTraceServiceRequest> traces;
  List<ExportLogsServiceRequest> logs;
}
