/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.logs.ch;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Builder
@Getter
public class ChLogsTableRow {
  long ts_ns;
  String log_stream;
  String service_name;
  int log_level;
  String severity_text;
  String trace_id;
  String span_id;
  String body;
  Map<String, String> resource_attribs_str_0;
  Map<String, String> resource_attribs_str_1;
  Map<String, String> resource_attribs_str_2;
  Map<String, String> resource_attribs_str_3;
  Map<String, String> resource_attribs_str_4;
  Map<String, String> resource_attribs_str_5;
  Map<String, String> resource_attribs_str_6;
  Map<String, String> resource_attribs_str_7;
  Map<String, String> resource_attribs_str_8;
  Map<String, String> resource_attribs_str_9;
  Map<String, Double> resource_attribs_number_0;
  Map<String, Double> resource_attribs_number_1;
  Map<String, Double> resource_attribs_number_2;
  Map<String, Double> resource_attribs_number_3;
  Map<String, Double> resource_attribs_number_4;
  Map<String, Double> resource_attribs_number_5;
  Map<String, Double> resource_attribs_number_6;
  Map<String, Double> resource_attribs_number_7;
  Map<String, Double> resource_attribs_number_8;
  Map<String, Double> resource_attribs_number_9;
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
