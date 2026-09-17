/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

public class ChConstants {
  public static final String TBL_GAUGES = "okapi_metrics.gauge_raw_samples";
  public static final String TBL_HISTOS = "okapi_metrics.histo_raw_samples";
  public static final String TBL_EXPONENTIAL_HISTOS = "okapi_metrics.exponential_histo_raw_samples";
  public static final String TBL_SUM = "okapi_metrics.sums_raw_samples";
  public static final String TBL_EXEMPLAR = "okapi_metrics.metric_exemplars";
  public static final String TBL_METRIC_EVENTS_META = "okapi_metrics.metric_events_stream_meta";
  public static final String TBL_SERVICE_RED_EVENTS = "okapi_traces.service_red_events";
  public static final String TBL_SPANS_V1 = "okapi_traces.spans_table_v1";
  public static final String TBL_SPANS_INGESTED_ATTRIBS = "okapi_traces.spans_ingested_attribs";
  public static final String TBL_LOGS_V1 = "okapi_logs.logs_table_v1";
  public static final int METRIC_HINTS_LIMIT = 500;
  public static final int TRACE_QUERY_LIMIT = 1000;
  public static final int LOGS_QUERY_LIMIT = 1000;
  public static final int TRACE_HINTS_LIMITS = 100;
}
