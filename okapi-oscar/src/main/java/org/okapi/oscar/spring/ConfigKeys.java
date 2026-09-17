/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.spring;

public class ConfigKeys {
  public static final String CLUSTER_EP = "${okapi.oscar.cluster.endpoint}";
  public static final String CONTRIB_TOOL_THREAD_COUNT =
      "${okapi.oscar.filter-contrib-tool.thread-count}";
  public static final String CONTRIB_TOOL_THROTTLE =
      "${okapi.oscar.filter-contrib-tool.throttle-limit}";
  public static final String CONTRIB_TOOL_DURATION_MILLIS =
      "${okapi.oscar.filter-contrib-tool.duration-millis}";
  public static final String INFERENCE_N_THREADS = "${okapi.oscar.inference.nthreads}";
}
