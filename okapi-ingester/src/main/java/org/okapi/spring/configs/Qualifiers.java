/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.spring.configs;

public class Qualifiers {

  // query windows for logs, traces, metrics on disk
  public static final String DISK_QP_MAX_QUERY_WIN = "diskQpMaxQueryWin";

  // query windows for peers
  public static final String METRICS_PEER_QUERY_TIMEOUT = "metricsPeerQueryTimeout";

  // various executors
  public static final String EXEC_PEER_METRICS = "peerMetricsExecutor";
  public static final String EXEC_METRICS_MULTI_SOURCE = "metricsMultiSourceQpExecutor";

  // various sharding orchestrators
  public static final String METRICS_SHARDING_ORCHESTRATOR = "shardingOrchestratorMetrics";
  public static final String TRACES_SHARDING_ORCHESTRATOR = "shardingOrchestratorTraces";
  public static final String LOGS_SHARDING_ORCHESTRATOR = "shardingOrchestratorLogs";

  // various consumer controllers
  public static final String METRICS_WAL_CONSUMER_CONTROLLERS = "metricsWalConsumerControllers";
  public static final String TRACES_WAL_CONSUMER_CONTROLLERS = "tracesWalConsumerControllers";
  public static final String LOGS_WAL_CONSUMER_CONTROLLERS = "logsWalConsumerControllers";

  // various zk clients
  public static final String METRICS_NS_ZK_CLIENT = "metricsNsZkClient";
  public static final String TRACES_NS_ZK_CLIENT = "tracesNsZkClient";
  public static final String LOGS_NS_ZK_CLIENT = "logsNsZkClient";

  // various shard update listeners
  public static final String METRICS_SHARD_UPDATE_LISTENER = "metricsShardUpdateListener";
  public static final String TRACES_SHARD_UPDATE_LISTENER = "tracesShardUpdateListener";
  public static final String LOGS_SHARD_UPDATE_LISTENER = "logsShardUpdateListener";

  // wal resources
  public static final String METRICS_WAL_RESOURCES = "metricsWalResources";
  public static final String TRACES_WAL_RESOURCES = "tracesWalResources";
  public static final String LOGS_WAL_RESOURCES = "logsWalResources";

  // ch wal resources
  public static final String METRICS_CH_WAL_RESOURCES = "metricsWalResources";
  public static final String TRACES_CH_WAL_RESOURCES = "tracesWalResources";
  public static final String LOGS_CH_WAL_RESOURCES = "logsWalResources";
}
