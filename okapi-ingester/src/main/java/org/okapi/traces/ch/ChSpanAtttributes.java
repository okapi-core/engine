/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.*;

public final class ChSpanAtttributes {
  private static final List<String> DEFAULT_ATTRIBUTES =
      List.of(
          "trace_id",
          "span_id",
          "service_name",
          "service_peer_name",
          "http_method",
          "http_status_code",
          "http_request_size",
          "http_response_size",
          "http_origin",
          "http_host",
          "server_address",
          "server_port",
          "client_address",
          "client_port",
          "source_address",
          "source_port",
          "network_protocol_type",
          "network_protocol_version",
          "db_system_name",
          "db_collection_name",
          "db_namespace",
          "db_operation_name",
          "db_response_status_code",
          "db_query_text",
          "db_query_summary",
          "db_stored_procedure_name",
          "db_response_returned_rows",
          "rpc_method",
          "rpc_method_original",
          "rpc_response_status_code");

  private static final Set<String> NUMERIC_DEFAULT_ATTRIBUTES =
      Set.of(
          "http_status_code",
          "http_request_size",
          "http_response_size",
          "server_address",
          "server_port",
          "client_port",
          "source_port",
          "db_response_status_code",
          "db_response_returned_rows",
          "rpc_response_status_code");

  private static final Map<String, String> DEFAULT_ATTRIBUTE_TYPES = buildTypeMap();

  private ChSpanAtttributes() {}

  public static List<String> getDefaultAttributes() {
    return DEFAULT_ATTRIBUTES;
  }

  public static boolean isDefault(String attr) {
    if (attr == null || attr.isEmpty()) return false;
    return DEFAULT_ATTRIBUTES.contains(attr);
  }

  public static boolean isNumeric(String attr) {
    if (attr == null || attr.isEmpty()) return false;
    return NUMERIC_DEFAULT_ATTRIBUTES.contains(attr);
  }

  public static String typeOf(String attr) {
    return DEFAULT_ATTRIBUTE_TYPES.getOrDefault(attr, "string");
  }

  private static Map<String, String> buildTypeMap() {
    var map = new HashMap<String, String>();
    for (var attr : DEFAULT_ATTRIBUTES) {
      map.put(attr, NUMERIC_DEFAULT_ATTRIBUTES.contains(attr) ? "number" : "string");
    }
    return Collections.unmodifiableMap(map);
  }

  public static String getValueExpr(String attr, boolean isNumeric) {
    if (isDefault(attr)) {
      return attr;
    } else {
      var colBucket = ChSpanAttributeBucketer.bucketForKey(attr);
      if (isNumeric) {
        return "attribs_number_" + colBucket + "['" + attr + "']";
      } else {
        return "attribs_string_" + colBucket + "['" + attr + "']";
      }
    }
  }
}
