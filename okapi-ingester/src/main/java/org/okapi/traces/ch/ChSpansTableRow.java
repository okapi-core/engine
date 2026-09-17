/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.ch;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class ChSpansTableRow {
  long ts_start_ns;
  long ts_end_ns;
  String span_id;
  SpanStatus span_status;
  String parent_span_id;
  String trace_id;
  String kind;
  String kind_string;
  String service_name;
  String service_peer_name;
  String http_method;
  Integer http_status_code;
  Integer http_request_size;
  Integer http_response_size;
  String http_origin;
  String http_host;
  Integer server_address;
  Integer server_port;
  String client_address;
  Integer client_port;
  String source_address;
  Integer source_port;
  String network_protocol_type;
  String network_protocol_version;
  String db_system_name;
  String db_collection_name;
  String db_namespace;
  String db_operation_name;
  Integer db_response_status_code;
  String db_query_text;
  String db_query_summary;
  String db_stored_procedure_name;
  Integer db_response_returned_rows;
  String rpc_method;
  String rpc_method_original;
  Integer rpc_response_status_code;
  Map<String, String> attribs_str_0;
  Map<String, String> attribs_str_1;
  Map<String, String> attribs_str_2;
  Map<String, String> attribs_str_3;
  Map<String, String> attribs_str_4;
  Map<String, String> attribs_str_5;
  Map<String, String> attribs_str_6;
  Map<String, String> attribs_str_7;
  Map<String, String> attribs_str_8;
  Map<String, String> attribs_str_9;
  Map<String, Double> attribs_number_0;
  Map<String, Double> attribs_number_1;
  Map<String, Double> attribs_number_2;
  Map<String, Double> attribs_number_3;
  Map<String, Double> attribs_number_4;
  Map<String, Double> attribs_number_5;
  Map<String, Double> attribs_number_6;
  Map<String, Double> attribs_number_7;
  Map<String, Double> attribs_number_8;
  Map<String, Double> attribs_number_9;
}
