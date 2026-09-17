/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service;

public class Configs {
  public static final String CLUSTER_EP = "${clusterEndpoint}";
  public static final String OSCAR_EP = "${oscarEndpoint}";
  public static final String CONCURRENT_QUERY_THREADS = "${query.threads}";
  public static final String CONCURRENCY_QUERY_LIM = "${query.nParallel}";
  public static final String QUERY_TIMEOUT = "${query.timeout}";
}
