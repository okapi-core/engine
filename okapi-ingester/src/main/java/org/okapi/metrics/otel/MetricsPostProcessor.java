/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.otel;

import java.util.List;
import org.okapi.rest.metrics.ExportMetricsRequest;

public interface MetricsPostProcessor {
  List<ExportMetricsRequest> process(List<ExportMetricsRequest> input);
}
