/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.engine.api;

import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import org.okapi.rest.logs.ChLogsFieldValuesRequest;
import org.okapi.rest.logs.ChLogsFieldValuesResponse;
import org.okapi.rest.logs.ChLogsFieldsRequest;
import org.okapi.rest.logs.ChLogsFieldsResponse;
import org.okapi.rest.logs.LogsQueryRequestV2;
import org.okapi.rest.logs.LogsQueryResponseV2;
import org.okapi.rest.logs.LogsSummaryRequest;
import org.okapi.rest.logs.LogsSummaryResponse;
import org.okapi.rest.logs.OkapiLogQlRequest;
import org.okapi.rest.logs.OkapiLogQlResponse;

public interface LogsEngine {
  LogsQueryResponseV2 query(LogsQueryRequestV2 request) throws Exception;

  ChLogsFieldsResponse fields(ChLogsFieldsRequest request) throws Exception;

  ChLogsFieldValuesResponse fieldValues(ChLogsFieldValuesRequest request) throws Exception;

  OkapiLogQlResponse queryWithLogQl(OkapiLogQlRequest request);

  LogsSummaryResponse summary(LogsSummaryRequest request) throws Exception;

  void ingest(ExportLogsServiceRequest request) throws Exception;
}
