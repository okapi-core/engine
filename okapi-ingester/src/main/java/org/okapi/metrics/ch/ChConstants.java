/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch;

public class ChConstants {
  public static final String TBL_GAUGES_LOCAL = "okapi_metrics.gauge_raw_samples_local";
  public static final String TBL_GAUGES_DIST = "okapi_metrics.gauge_raw_samples_dist";
  public static final String TBL_HISTOS_LOCAL = "okapi_metrics.histo_raw_samples_local";
  public static final String TBL_HISTOS_DIST = "okapi_metrics.histo_raw_samples_dist";
  public static final String TBL_EXPONENTIAL_HISTOS_LOCAL =
      "okapi_metrics.exponential_histo_raw_samples_local";
  public static final String TBL_EXPONENTIAL_HISTOS_DIST =
      "okapi_metrics.exponential_histo_raw_samples_dist";
  public static final String TBL_SUM_LOCAL = "okapi_metrics.sums_raw_samples_local";
  public static final String TBL_SUM_DIST = "okapi_metrics.sums_raw_samples_dist";
  public static final String TBL_EXEMPLAR_LOCAL = "okapi_metrics.metric_exemplars_local";
  public static final String TBL_EXEMPLAR_DIST = "okapi_metrics.metric_exemplars_dist";
  public static final String TBL_METRIC_EVENTS_META_LOCAL =
      "okapi_metrics.metric_events_stream_meta_local";
  public static final String TBL_METRIC_EVENTS_META_DIST =
      "okapi_metrics.metric_events_stream_meta_dist";
  public static final String TBL_SERVICE_RED_EVENTS = "okapi_traces.service_red_events";
  public static final String TBL_SPANS_V1 = "okapi_traces.spans_table_v1";
  public static final String TBL_SPANS_INGESTED_ATTRIBS = "okapi_traces.spans_ingested_attribs";
  public static final String TBL_LOGS_V1 = "okapi_logs.logs_table_v1";
  public static final int METRIC_HINTS_LIMIT = 500;
  public static final int TRACE_QUERY_LIMIT = 1000;
  public static final int LOGS_QUERY_LIMIT = 1000;
  public static final int TRACE_HINTS_LIMITS = 100;
}
