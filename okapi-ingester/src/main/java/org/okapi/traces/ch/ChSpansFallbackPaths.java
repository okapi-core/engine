/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChSpansFallbackPaths {
  private static final Map<String, List<String>> PATHS =
      Map.ofEntries(
          Map.entry("service_peer_name", List.of("peer.service", "service.peer.name")),
          Map.entry("http_method", List.of("http.request.method", "http.method")),
          Map.entry("http_status_code", List.of("http.response.status_code", "http.status_code")),
          Map.entry(
              "http_request_size",
              List.of("http.request.body.size", "http.request_content_length")),
          Map.entry(
              "http_response_size",
              List.of("http.response.body.size", "http.response_content_length")),
          Map.entry("http_origin", List.of("http.request.header.origin", "http.origin")),
          Map.entry("http_host", List.of("http.request.header.host", "http.host")),
          Map.entry("server_address", List.of("server.address")),
          Map.entry("server_port", List.of("server.port")),
          Map.entry("client_address", List.of("client.address")),
          Map.entry("client_port", List.of("client.port")),
          Map.entry("source_address", List.of("source.address")),
          Map.entry("source_port", List.of("source.port")),
          Map.entry("network_protocol_type", List.of("network.protocol.name", "network.type")),
          Map.entry("network_protocol_version", List.of("network.protocol.version")),
          Map.entry("db_system_name", List.of("db.system", "db.system.name")),
          Map.entry("db_collection_name", List.of("db.collection.name")),
          Map.entry("db_namespace", List.of("db.namespace", "db.name")),
          Map.entry("db_operation_name", List.of("db.operation", "db.operation.name")),
          Map.entry("db_response_status_code", List.of("db.response.status_code")),
          Map.entry("db_query_text", List.of("db.query.text", "db.statement")),
          Map.entry("db_query_summary", List.of("db.query.summary")),
          Map.entry("db_stored_procedure_name", List.of("db.stored_procedure.name")),
          Map.entry("db_response_returned_rows", List.of("db.response.returned_rows")),
          Map.entry("rpc_method", List.of("rpc.method")),
          Map.entry("rpc_method_original", List.of("rpc.method.original", "rpc.method")),
          Map.entry("rpc_response_status_code", List.of("rpc.response.status_code")));

  private ChSpansFallbackPaths() {}

  public static List<String> getPaths(String column) {
    if (column == null || column.isEmpty()) return Collections.emptyList();
    return PATHS.getOrDefault(column, Collections.emptyList());
  }

  public static Set<String> getAllPaths() {
    var all = new HashSet<String>();
    all.addAll(PATHS.keySet());
    for (var paths : PATHS.values()) {
      if (paths == null) continue;
      all.addAll(paths);
    }
    return Collections.unmodifiableSet(all);
  }
}
